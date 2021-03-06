/**
 * ﻿============LICENSE_START=======================================================
 * org.onap.aai
 * ================================================================================
 * Copyright © 2017-2018 AT&T Intellectual Property. All rights reserved.
 * Copyright © 2017-2018 Amdocs
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */
package org.onap.crud.service;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.PreDestroy;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.onap.aai.cl.api.LogFields;
import org.onap.aai.cl.api.Logger;
import org.onap.aai.cl.eelf.LoggerFactory;
import org.onap.aai.cl.mdc.MdcContext;
import org.onap.aai.cl.mdc.MdcOverride;
import org.onap.aai.event.api.EventConsumer;
import org.onap.aai.event.api.EventPublisher;
import org.onap.aai.restclient.client.OperationResult;
import org.onap.crud.dao.GraphDao;
import org.onap.crud.entity.Edge;
import org.onap.crud.entity.Vertex;
import org.onap.crud.event.GraphEvent;
import org.onap.crud.event.GraphEvent.GraphEventOperation;
import org.onap.crud.event.GraphEventEdge;
import org.onap.crud.event.GraphEventVertex;
import org.onap.crud.event.envelope.GraphEventEnvelope;
import org.onap.crud.event.response.GraphEventResponseHandler;
import org.onap.crud.exception.CrudException;
import org.onap.crud.logging.CrudServiceMsgs;
import org.onap.crud.parser.CrudResponseBuilder;
import org.onap.crud.util.CrudProperties;
import org.onap.crud.util.CrudServiceConstants;
import org.onap.crud.util.CrudServiceUtil;
import org.onap.crud.util.etag.EtagGenerator;
import org.onap.schema.OxmModelValidator;
import org.onap.schema.RelationshipSchemaValidator;

public class CrudAsyncGraphDataService extends AbstractGraphDataService {

    private static Integer requestTimeOut;

    private EventPublisher asyncRequestPublisher;

    private Timer timer;

    public static final Integer DEFAULT_REQUEST_TIMEOUT = 30000;
    private static final Integer DEFAULT_ASYNC_RESPONSE_PROCESS_POLL_INTERVAL = 1000;

    private static Logger logger = LoggerFactory.getInstance().getLogger(CrudAsyncGraphDataService.class.getName());
    private static Logger metricsLogger =
            LoggerFactory.getInstance().getMetricsLogger(CrudAsyncGraphDataService.class.getName());
    private static LogFields okFields = new LogFields();
    private EtagGenerator etagGenerator;

    static {
        okFields.setField(Status.OK, Status.OK.toString());
    }

    private GraphEventResponseHandler responseHandler = new GraphEventResponseHandler();

    public static Integer getRequestTimeOut() {
        return requestTimeOut;
    }

    public CrudAsyncGraphDataService(GraphDao dao, EventPublisher asyncRequestPublisher,
            EventConsumer asyncResponseConsumer) throws CrudException, NoSuchAlgorithmException {
        this(dao, dao, asyncRequestPublisher, asyncResponseConsumer);
    }

    public CrudAsyncGraphDataService(GraphDao dao, GraphDao daoForGet, EventPublisher asyncRequestPublisher,
            EventConsumer asyncResponseConsumer) throws CrudException, NoSuchAlgorithmException {

        super();
        this.dao = dao;
        this.daoForGet = daoForGet;

        requestTimeOut = DEFAULT_REQUEST_TIMEOUT;
        try {
            requestTimeOut = Integer.parseInt(CrudProperties.get(CrudServiceConstants.CRD_ASYNC_REQUEST_TIMEOUT));
        } catch (NumberFormatException ex) {
            // Leave it as the default
        }

        Integer responsePollInterval = DEFAULT_ASYNC_RESPONSE_PROCESS_POLL_INTERVAL;
        try {
            responsePollInterval =
                    Integer.parseInt(CrudProperties.get(CrudServiceConstants.CRD_ASYNC_RESPONSE_PROCESS_POLL_INTERVAL));
        } catch (Exception ex) {
            logger.error(CrudServiceMsgs.ASYNC_DATA_SERVICE_ERROR, "Unable to parse "
                    + CrudServiceConstants.CRD_ASYNC_RESPONSE_PROCESS_POLL_INTERVAL + " error: " + ex.getMessage());
        }

        // Start the Response Consumer timer
        CrudAsyncResponseConsumer crudAsyncResponseConsumer = new CrudAsyncResponseConsumer(
                asyncResponseConsumer, new GraphEventUpdater()
                );
        timer = new Timer("crudAsyncResponseConsumer-1");
        timer.schedule(crudAsyncResponseConsumer, responsePollInterval, responsePollInterval);

        this.asyncRequestPublisher = asyncRequestPublisher;
        this.etagGenerator = new EtagGenerator();

        logger.info(CrudServiceMsgs.ASYNC_DATA_SERVICE_INFO, "CrudAsyncGraphDataService initialized SUCCESSFULLY!");
    }

    public class CollectGraphResponse implements Callable<GraphEventEnvelope> {
        private volatile GraphEventEnvelope graphEventEnvelope;
        private volatile CountDownLatch latch = new CountDownLatch(1);

        @Override
        public GraphEventEnvelope call() throws TimeoutException {
            try {
                // Wait until graphEvent is available
                latch.await(CrudAsyncGraphDataService.getRequestTimeOut(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                latch.countDown();
                // Restore interrupted state...
                Thread.currentThread().interrupt();
                if (this.graphEventEnvelope != null) {
                    return this.graphEventEnvelope;
                } else {
                    throw new TimeoutException();
                }
            }
            return this.graphEventEnvelope;
        }

        public void populateGraphEventEnvelope(GraphEventEnvelope eventEnvelope) {
            this.graphEventEnvelope = eventEnvelope;
            latch.countDown();
        }
    }

    private GraphEventEnvelope sendAndWait(GraphEvent event) throws CrudException {

        long startTimeInMs = System.currentTimeMillis();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        MdcOverride override = new MdcOverride();
        override.addAttribute(MdcContext.MDC_START_TIME, formatter.format(startTimeInMs));

        String eventEnvelopeJson = new GraphEventEnvelope(event).toJson();

        // publish to request queue
        try {
            asyncRequestPublisher.sendSync(eventEnvelopeJson);
        } catch (Exception e) {
            throw new CrudException(
                    "Error publishing request " + event.getTransactionId() + "  Cause: " + e.getMessage(),
                    Status.INTERNAL_SERVER_ERROR);
        }

        logger.debug(CrudServiceMsgs.ASYNC_DATA_SERVICE_INFO, "Event Sent =" + eventEnvelopeJson);

        logger.info(CrudServiceMsgs.ASYNC_DATA_SERVICE_INFO,
                "Event submitted of type: " + event.getObjectType() + " with key: " + event.getObjectKey()
                + " , transaction-id: " + event.getTransactionId() + " , operation: "
                + event.getOperation().toString());

        ExecutorService executor =
                Executors.newSingleThreadExecutor(new CrudThreadFactory("TX-" + event.getTransactionId()));
        CollectGraphResponse collector = new CollectGraphResponse();
        CrudAsyncGraphEventCache.put(event.getTransactionId(), collector);
        GraphEventEnvelope response;
        Future<GraphEventEnvelope> future = executor.submit(collector);
        try {
            response = future.get(requestTimeOut, TimeUnit.MILLISECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            CrudAsyncGraphEventCache.invalidate(event.getTransactionId());
            logger.error(CrudServiceMsgs.ASYNC_DATA_SERVICE_ERROR,
                    "Request timed out for transactionId: " + event.getTransactionId());
            future.cancel(true);
            if (e instanceof InterruptedException) {
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            }
            throw new CrudException("Timed out , transactionId: " + event.getTransactionId() + " , operation: "
                    + event.getOperation().toString(), Status.INTERNAL_SERVER_ERROR);
        } finally {
            // Kill the thread as the work is completed
            executor.shutdownNow();
        }
        metricsLogger.info(CrudServiceMsgs.ASYNC_DATA_SERVICE_INFO, okFields, override,
                "Total elapsed time for operation: " + event.getOperation().toString() + " , transactionId: "
                        + event.getTransactionId() + " is " + Long.toString(System.currentTimeMillis() - startTimeInMs)
                        + " ms");
        return response;
    }

    @Override
    public ImmutablePair<EntityTag, String> addVertex(String version, String type, VertexPayload payload)
            throws CrudException {
        // Validate the incoming payload
        Vertex vertex = OxmModelValidator.validateIncomingUpsertPayload(null, version, type, payload.getProperties());
        vertex.getProperties().put(OxmModelValidator.Metadata.NODE_TYPE.propertyName(), type);
        // Create graph request event
        GraphEvent event = GraphEvent.builder(GraphEventOperation.CREATE)
                .vertex(GraphEventVertex.fromVertex(vertex, version)).build();

        GraphEventEnvelope response = sendAndWait(event);

        EntityTag entityTag;
        try {
            entityTag = new EntityTag(etagGenerator.computeHashForVertex(response.getBody().getVertex()));
        } catch (IOException e) {
            throw new CrudException(e);
        }
        String responsePayload = responseHandler.handleVertexResponse(version, event, response);

        return new ImmutablePair<>(entityTag, responsePayload);
    }

    @Override
    public ImmutablePair<EntityTag, String> addEdge(String version, String type, EdgePayload payload)
            throws CrudException {
        Edge edge = RelationshipSchemaValidator.validateIncomingAddPayload(version, type, payload);

        // Create graph request event
        GraphEvent event =
                GraphEvent.builder(GraphEventOperation.CREATE).edge(GraphEventEdge.fromEdge(edge, version)).build();

        GraphEventEnvelope response = sendAndWait(event);

        EntityTag entityTag;
        try {
            entityTag = new EntityTag(etagGenerator.computeHashForEdge(response.getBody().getEdge()));
        } catch (IOException e) {
            throw new CrudException(e);
        }
        String responsePayload = responseHandler.handleEdgeResponse(version, event, response);

        return new ImmutablePair<>(entityTag, responsePayload);
    }

    @Override
    public ImmutablePair<EntityTag, String> updateVertex(String version, String id, String type, VertexPayload payload)
            throws CrudException {
        Vertex vertex = OxmModelValidator.validateIncomingUpsertPayload(id, version, type, payload.getProperties());
        GraphEvent event = GraphEvent.builder(GraphEventOperation.UPDATE)
                .vertex(GraphEventVertex.fromVertex(vertex, version)).build();

        GraphEventEnvelope response = sendAndWait(event);

        EntityTag entityTag;
        try {
            entityTag = new EntityTag(etagGenerator.computeHashForVertex(response.getBody().getVertex()));
        } catch (IOException e) {
            throw new CrudException(e);
        }
        String responsePayload = responseHandler.handleVertexResponse(version, event, response);

        return new ImmutablePair<>(entityTag, responsePayload);
    }

    @Override
    public ImmutablePair<EntityTag, String> patchVertex(String version, String id, String type, VertexPayload payload)
            throws CrudException {
        OperationResult existingVertexOpResult = dao.getVertex(id, OxmModelValidator.resolveCollectionType(version, type), version,
                new HashMap<String, String>());
        Vertex existingVertex = Vertex.fromJson(existingVertexOpResult.getResult(), version);
        Vertex patchedVertex = OxmModelValidator.validateIncomingPatchPayload(id, version, type,
                payload.getProperties(), existingVertex);
        GraphEvent event = GraphEvent.builder(GraphEventOperation.UPDATE)
                .vertex(GraphEventVertex.fromVertex(patchedVertex, version)).build();

        GraphEventEnvelope response = sendAndWait(event);

        EntityTag entityTag;
        try {
            entityTag = new EntityTag(etagGenerator.computeHashForVertex(response.getBody().getVertex()));
        } catch (IOException e) {
            throw new CrudException(e);
        }
        String responsePayload = responseHandler.handleVertexResponse(version, event, response);

        return new ImmutablePair<>(entityTag, responsePayload);
    }

    @Override
    public String deleteVertex(String version, String id, String type) throws CrudException {
        type = OxmModelValidator.resolveCollectionType(version, type);
        GraphEvent event = GraphEvent.builder(GraphEventOperation.DELETE)
                .vertex(new GraphEventVertex(id, version, type, null)).build();

        GraphEventEnvelope response = sendAndWait(event);
        return responseHandler.handleDeletionResponse(event, response);
    }

    @Override
    public String deleteEdge(String version, String id, String type) throws CrudException {
        RelationshipSchemaValidator.validateType(version, type);
        GraphEvent event = GraphEvent.builder(GraphEventOperation.DELETE)
                .edge(new GraphEventEdge(id, version, type, null, null, null)).build();

        GraphEventEnvelope response = sendAndWait(event);
        return responseHandler.handleDeletionResponse(event, response);
    }

    @Override
    public ImmutablePair<EntityTag, String> updateEdge(String version, String id, String type, EdgePayload payload)
            throws CrudException {
        OperationResult operationResult = dao.getEdge(id, type, new HashMap<String, String>());
        Edge edge = Edge.fromJson(operationResult.getResult());
        Edge validatedEdge = RelationshipSchemaValidator.validateIncomingUpdatePayload(edge, version, payload);

        GraphEvent event = GraphEvent.builder(GraphEventOperation.UPDATE)
                .edge(GraphEventEdge.fromEdge(validatedEdge, version)).build();

        GraphEventEnvelope response = sendAndWait(event);

        EntityTag entityTag;
        try {
            entityTag = new EntityTag(etagGenerator.computeHashForEdge(response.getBody().getEdge()));
        } catch (IOException e) {
            throw new CrudException(e);
        }
        String responsePayload = responseHandler.handleEdgeResponse(version, event, response);

        return new ImmutablePair<>(entityTag, responsePayload);
    }

    @Override
    public ImmutablePair<EntityTag, String> patchEdge(String version, String id, String type, EdgePayload payload)
            throws CrudException {
        OperationResult operationResult = dao.getEdge(id, type, new HashMap<String, String>());
        Edge edge = Edge.fromJson(operationResult.getResult());
        Edge patchedEdge = RelationshipSchemaValidator.validateIncomingPatchPayload(edge, version, payload);
        GraphEvent event = GraphEvent.builder(GraphEventOperation.UPDATE)
                .edge(GraphEventEdge.fromEdge(patchedEdge, version)).build();

        GraphEventEnvelope response = sendAndWait(event);

        EntityTag entityTag;
        try {
            entityTag = new EntityTag(etagGenerator.computeHashForEdge(response.getBody().getEdge()));
        } catch (IOException e) {
            throw new CrudException(e);
        }
        String responsePayload = responseHandler.handleEdgeResponse(version, event, response);

        return new ImmutablePair<>(entityTag, responsePayload);
    }

    @PreDestroy
    protected void preShutdown() {
        timer.cancel();
    }

    private Vertex addBulkVertex(Vertex vertex, String version, String dbTransId) throws CrudException {
        GraphEvent event = GraphEvent.builder(GraphEventOperation.CREATE)
                .vertex(GraphEventVertex.fromVertex(vertex, version)).build();
        event.setDbTransactionId(dbTransId);
        GraphEvent response = publishEvent(event);
        return response.getVertex().toVertex();
    }

    private Vertex updateBulkVertex(Vertex vertex, String id, String version, String dbTransId) throws CrudException {
        GraphEvent event = GraphEvent.builder(GraphEventOperation.UPDATE)
                .vertex(GraphEventVertex.fromVertex(vertex, version)).build();
        event.setDbTransactionId(dbTransId);
        GraphEvent response = publishEvent(event);
        return response.getVertex().toVertex();
    }

    private void deleteBulkVertex(String id, String version, String type, String dbTransId) throws CrudException {
        GraphEvent event = GraphEvent.builder(GraphEventOperation.DELETE)
                .vertex(new GraphEventVertex(id, version, type, null)).build();
        event.setDbTransactionId(dbTransId);
        publishEvent(event);
    }

    private Edge addBulkEdge(Edge edge, String version, String dbTransId) throws CrudException {
        GraphEvent event =
                GraphEvent.builder(GraphEventOperation.CREATE).edge(GraphEventEdge.fromEdge(edge, version)).build();
        event.setDbTransactionId(dbTransId);
        GraphEvent response = publishEvent(event);
        return response.getEdge().toEdge();
    }

    private Edge updateBulkEdge(Edge edge, String version, String dbTransId) throws CrudException {
        GraphEvent event =
                GraphEvent.builder(GraphEventOperation.UPDATE).edge(GraphEventEdge.fromEdge(edge, version)).build();
        event.setDbTransactionId(dbTransId);
        GraphEvent response = publishEvent(event);
        return response.getEdge().toEdge();
    }

    private void deleteBulkEdge(String id, String version, String dbTransId) throws CrudException {
        // Get the edge type
        String type = null;
        try {
            Edge edge = daoForGet.getEdge(id);
            type = edge.getType();
        }
        catch (CrudException ex) {
            // Likely the client is trying to delete an edge which isn't present.  Just swallow the exception
            // and let the bulk request fail via the normal path.
        }

        GraphEvent event = GraphEvent.builder(GraphEventOperation.DELETE)
                .edge(new GraphEventEdge(id, version, type, null, null, null)).build();
        event.setDbTransactionId(dbTransId);
        publishEvent(event);
    }

    @Override
    public String addBulk(String version, BulkPayload payload, HttpHeaders headers) throws CrudException {
        HashMap<String, Vertex> vertices = new HashMap<>();
        HashMap<String, Edge> edges = new HashMap<>();

        String txId = dao.openTransaction();

        try {
            // Step 1. Handle edge deletes (must happen before vertex deletes)
            for (JsonElement v : payload.getRelationships()) {
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<Map.Entry<String, JsonElement>>(
                        v.getAsJsonObject().entrySet());

                if (entries.size() != 2) {
                    throw new CrudException("", Status.BAD_REQUEST);
                }
                Map.Entry<String, JsonElement> opr = entries.get(0);
                Map.Entry<String, JsonElement> item = entries.get(1);
                EdgePayload edgePayload = EdgePayload.fromJson(item.getValue().getAsJsonObject().toString());

                if (opr.getValue().getAsString().equalsIgnoreCase("delete")) {
                    deleteBulkEdge(edgePayload.getId(), version, txId);
                }
            }

            // Step 2: Handle vertex deletes
            for (JsonElement v : payload.getObjects()) {
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<Map.Entry<String, JsonElement>>(
                        v.getAsJsonObject().entrySet());

                if (entries.size() != 2) {
                    throw new CrudException("", Status.BAD_REQUEST);
                }

                Map.Entry<String, JsonElement> opr = entries.get(0);
                Map.Entry<String, JsonElement> item = entries.get(1);
                VertexPayload vertexPayload = VertexPayload.fromJson(item.getValue().getAsJsonObject().toString());

                if (opr.getValue().getAsString().equalsIgnoreCase("delete")) {
                    String type = OxmModelValidator.resolveCollectionType(version, vertexPayload.getType());
                    deleteBulkVertex(vertexPayload.getId(), version, type, txId);
                }
            }

            // Step 3: Handle vertex add/modify (must happen before edge adds)
            for (JsonElement v : payload.getObjects()) {
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<Map.Entry<String, JsonElement>>(
                        v.getAsJsonObject().entrySet());

                if (entries.size() != 2) {
                    throw new CrudException("", Status.BAD_REQUEST);
                }
                Map.Entry<String, JsonElement> opr = entries.get(0);
                Map.Entry<String, JsonElement> item = entries.get(1);
                VertexPayload vertexPayload = VertexPayload.fromJson(item.getValue().getAsJsonObject().toString());

                // Add vertex
                if (opr.getValue().getAsString().equalsIgnoreCase("add")) {
                    vertexPayload.setProperties(CrudServiceUtil.mergeHeaderInFoToPayload(vertexPayload.getProperties(),
                            headers, true));
                    Vertex validatedVertex = OxmModelValidator.validateIncomingUpsertPayload(null, version, vertexPayload.getType(),
                            vertexPayload.getProperties());
                    Vertex persistedVertex = addBulkVertex(validatedVertex, version, txId);
                    Vertex outgoingVertex = OxmModelValidator.validateOutgoingPayload(version, persistedVertex);
                    vertices.put(item.getKey(), outgoingVertex);
                }

                // Update vertex
                else if (opr.getValue().getAsString().equalsIgnoreCase("modify")) {
                    vertexPayload.setProperties(CrudServiceUtil.mergeHeaderInFoToPayload(vertexPayload.getProperties(),
                            headers, false));
                    Vertex validatedVertex = OxmModelValidator.validateIncomingUpsertPayload(vertexPayload.getId(), version,
                            vertexPayload.getType(), vertexPayload.getProperties());
                    Vertex persistedVertex = updateBulkVertex(validatedVertex, vertexPayload.getId(), version, txId);
                    Vertex outgoingVertex = OxmModelValidator.validateOutgoingPayload(version, persistedVertex);
                    vertices.put(item.getKey(), outgoingVertex);
                }

                // Patch vertex
                else if (opr.getValue().getAsString().equalsIgnoreCase("patch")) {
                    if ( (vertexPayload.getId() == null) || (vertexPayload.getType() == null) ) {
                        throw new CrudException("id and type must be specified for patch request", Status.BAD_REQUEST);
                    }

                    vertexPayload.setProperties(CrudServiceUtil.mergeHeaderInFoToPayload(vertexPayload.getProperties(),
                            headers, false));

                    OperationResult existingVertexOpResult = dao.getVertex(vertexPayload.getId(), OxmModelValidator.resolveCollectionType(version, vertexPayload.getType()), version, new HashMap<String, String>());
                    Vertex existingVertex = Vertex.fromJson(existingVertexOpResult.getResult(), version);
                    Vertex validatedVertex = OxmModelValidator.validateIncomingPatchPayload(vertexPayload.getId(),
                            version, vertexPayload.getType(), vertexPayload.getProperties(), existingVertex);
                    Vertex persistedVertex = updateBulkVertex(validatedVertex, vertexPayload.getId(), version, txId);
                    Vertex outgoingVertex = OxmModelValidator.validateOutgoingPayload(version, persistedVertex);
                    vertices.put(item.getKey(), outgoingVertex);
                }
            }

            // Step 4: Handle edge add/modify
            for (JsonElement v : payload.getRelationships()) {
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<Map.Entry<String, JsonElement>>(
                        v.getAsJsonObject().entrySet());

                if (entries.size() != 2) {
                    throw new CrudException("", Status.BAD_REQUEST);
                }
                Map.Entry<String, JsonElement> opr = entries.get(0);
                Map.Entry<String, JsonElement> item = entries.get(1);
                EdgePayload edgePayload = EdgePayload.fromJson(item.getValue().getAsJsonObject().toString());

                // Add/Update edge
                if (opr.getValue().getAsString().equalsIgnoreCase("add")
                        || opr.getValue().getAsString().equalsIgnoreCase("modify")
                        || opr.getValue().getAsString().equalsIgnoreCase("patch")) {
                    Edge validatedEdge;
                    Edge persistedEdge;
                    if (opr.getValue().getAsString().equalsIgnoreCase("add")) {
                        // Fix the source/destination
                        if (edgePayload.getSource().startsWith("$")) {
                            Vertex source = vertices.get(edgePayload.getSource().substring(1));
                            if (source == null) {
                                throw new CrudException("Not able to find vertex: " + edgePayload.getSource().substring(1),
                                        Status.INTERNAL_SERVER_ERROR);
                            }
                            edgePayload
                            .setSource("services/inventory/" + version + "/" + source.getType() + "/" + source.getId().get());
                        }
                        if (edgePayload.getTarget().startsWith("$")) {
                            Vertex target = vertices.get(edgePayload.getTarget().substring(1));
                            if (target == null) {
                                throw new CrudException("Not able to find vertex: " + edgePayload.getTarget().substring(1),
                                        Status.INTERNAL_SERVER_ERROR);
                            }
                            edgePayload
                            .setTarget("services/inventory/" + version + "/" + target.getType() + "/" + target.getId().get());
                        }

                        // If the type isn't set, resolve it based on on the sourece and target vertex types
                        if (edgePayload.getType() == null || edgePayload.getType().isEmpty()) {
                            edgePayload.setType(CrudServiceUtil.determineEdgeType(edgePayload, version));
                        }
                        
                        validatedEdge = RelationshipSchemaValidator.validateIncomingAddPayload(version, edgePayload.getType(),edgePayload);
                        persistedEdge = addBulkEdge(validatedEdge, version, txId);
                    } else if (opr.getValue().getAsString().equalsIgnoreCase("modify")) {
                        Edge edge = dao.getEdge(edgePayload.getId(), txId);

                        // If the type isn't set, resolve it based on on the sourece and target vertex types
                        if (edgePayload.getType() == null || edgePayload.getType().isEmpty()) {
                            edgePayload.setType(edge.getType());
                        }

                        validatedEdge = RelationshipSchemaValidator.validateIncomingUpdatePayload(edge, version, edgePayload);
                        persistedEdge = updateBulkEdge(validatedEdge, version, txId);
                    } else {
                        if (edgePayload.getId() == null) {
                            throw new CrudException("id must be specified for patch request", Status.BAD_REQUEST);
                        }
                        Edge existingEdge = dao.getEdge(edgePayload.getId(), txId);

                        // If the type isn't set, resolve it based on on the sourece and target vertex types
                        if (edgePayload.getType() == null || edgePayload.getType().isEmpty()) {
                            edgePayload.setType(existingEdge.getType());
                        }

                        Edge patchedEdge = RelationshipSchemaValidator.validateIncomingPatchPayload(existingEdge, version, edgePayload);
                        persistedEdge = updateBulkEdge(patchedEdge, version, txId);
                    }


                    Edge outgoingEdge = RelationshipSchemaValidator.validateOutgoingPayload(version, persistedEdge);
                    edges.put(item.getKey(), outgoingEdge);
                }
            }

            // commit transaction
            dao.commitTransaction(txId);
        } catch (CrudException ex) {
            dao.rollbackTransaction(txId);
            throw ex;
        } catch (Exception ex) {
            dao.rollbackTransaction(txId);
            throw ex;
        } finally {
            if (dao.transactionExists(txId)) {
                dao.rollbackTransaction(txId);
            }
        }

        return CrudResponseBuilder.buildUpsertBulkResponse(vertices, edges, version, payload);
    }

    private GraphEvent publishEvent(GraphEvent event) throws CrudException {
        GraphEventEnvelope response = sendAndWait(event);
        responseHandler.handleBulkEventResponse(event, response);
        return response.getBody();
    }
}