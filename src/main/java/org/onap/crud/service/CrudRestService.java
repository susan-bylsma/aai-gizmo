/**
 * ﻿============LICENSE_START=======================================================
 * Gizmo
 * ================================================================================
 * Copyright © 2017 AT&T Intellectual Property.
 * Copyright © 2017 Amdocs
 * All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 *
 * ECOMP is a trademark and service mark of AT&T Intellectual Property.
 */
package org.onap.crud.service;

import java.security.cert.X509Certificate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.security.auth.x500.X500Principal;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.ext.PATCH;
import org.onap.aaiauth.auth.Auth;
import org.onap.aai.cl.api.Logger;
import org.onap.aai.cl.eelf.LoggerFactory;
import org.onap.aai.db.props.AAIProperties;
import org.onap.crud.exception.CrudException;
import org.onap.crud.logging.CrudServiceMsgs;
import org.onap.crud.logging.LoggingUtil;
import org.onap.crud.util.CrudServiceConstants;
import org.slf4j.MDC;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class CrudRestService {

  private AbstractGraphDataService graphDataService;
  Logger logger = LoggerFactory.getInstance().getLogger(CrudRestService.class.getName());
  Logger auditLogger = LoggerFactory.getInstance().getAuditLogger(CrudRestService.class.getName());
  private Auth auth;

  Gson gson = new Gson();

  private String mediaType = MediaType.APPLICATION_JSON;
  public static final String HTTP_PATCH_METHOD_OVERRIDE = "X-HTTP-Method-Override";

  public CrudRestService(AbstractGraphDataService graphDataService) throws Exception {
    this.graphDataService = graphDataService;
    this.auth = new Auth(CrudServiceConstants.CRD_AUTH_FILE);
  }

  public enum Action {
    POST, GET, PUT, DELETE, PATCH
  }

  public void startup() {

  }

  @GET
  @Path("/{version}/{type}/{id}")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response getVertex(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {
    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.GET, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        String result = graphDataService.getVertex(version, id, type);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @GET
  @Path("/{version}/{type}/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response getVertices(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo uriInfo,
      @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;
    if (validateRequest(req, uri, content, Action.GET, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      Map<String, String> filter = new HashMap<String, String>();
      for (Map.Entry<String, List<String>> e : uriInfo.getQueryParameters().entrySet()) {
        filter.put(e.getKey(), e.getValue().get(0));
      }

      try {
        String result = graphDataService.getVertices(version, type, filter);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @GET
  @Path("/relationships/{version}/{type}/{id}")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response getEdge(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {
    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.GET, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {

        String result = graphDataService.getEdge(version, id, type);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @GET
  @Path("/relationships/{version}/{type}/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response getEdges(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo uriInfo,
      @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.GET, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      Map<String, String> filter = new HashMap<String, String>();
      for (Map.Entry<String, List<String>> e : uriInfo.getQueryParameters().entrySet()) {
        filter.put(e.getKey(), e.getValue().get(0));
      }

      try {
        String result = graphDataService.getEdges(version, type, filter);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();

    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @PUT
  @Path("/relationships/{version}/{type}/{id}")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response updateEdge(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.PUT, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        EdgePayload payload = EdgePayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null && !payload.getId().equals(id)) {
          throw new CrudException("ID Mismatch", Status.BAD_REQUEST);
        }
        String result;

        if (headers.getRequestHeaders().getFirst(HTTP_PATCH_METHOD_OVERRIDE) != null
            && headers.getRequestHeaders().getFirst(HTTP_PATCH_METHOD_OVERRIDE).equalsIgnoreCase("PATCH")) {
          result = graphDataService.patchEdge(version, id, type, payload);
        } else {

          result = graphDataService.updateEdge(version, id, type, payload);
        }

        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();

    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @PATCH
  @Path("/relationships/{version}/{type}/{id}")
  @Consumes({ "application/merge-patch+json" })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response patchEdge(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;
    if (validateRequest(req, uri, content, Action.PATCH, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        EdgePayload payload = EdgePayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null && !payload.getId().equals(id)) {
          throw new CrudException("ID Mismatch", Status.BAD_REQUEST);
        }

        String result = graphDataService.patchEdge(version, id, type, payload);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @PUT
  @Path("/{version}/{type}/{id}")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response updateVertex(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.PUT, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        VertexPayload payload = VertexPayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null && !payload.getId().equals(id)) {
          throw new CrudException("ID Mismatch", Status.BAD_REQUEST);
        }
        String result;
        
        payload.setProperties(mergeHeaderInFoToPayload(payload.getProperties(), headers, false));

        if (headers.getRequestHeaders().getFirst(HTTP_PATCH_METHOD_OVERRIDE) != null
            && headers.getRequestHeaders().getFirst(HTTP_PATCH_METHOD_OVERRIDE).equalsIgnoreCase("PATCH")) {
          result = graphDataService.patchVertex(version, id, type, payload);
        } else {

          result = graphDataService.updateVertex(version, id, type, payload);
        }
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @PATCH
  @Path("/{version}/{type}/{id}")
  @Consumes({ "application/merge-patch+json" })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response patchVertex(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.PATCH, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {
      try {
        VertexPayload payload = VertexPayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null && !payload.getId().equals(id)) {
          throw new CrudException("ID Mismatch", Status.BAD_REQUEST);
        }

        payload.setProperties(mergeHeaderInFoToPayload(payload.getProperties(), headers, false));

        String result = graphDataService.patchVertex(version, id, type, payload);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @POST
  @Path("/{version}/{type}/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response addVertex(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo uriInfo,
      @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.POST, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        VertexPayload payload = VertexPayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null) {
          throw new CrudException("ID specified , use Http PUT to update Vertex", Status.BAD_REQUEST);
        }

        if (payload.getType() != null && !payload.getType().equals(type)) {
          throw new CrudException("Vertex Type mismatch", Status.BAD_REQUEST);
        }

        payload.setProperties(mergeHeaderInFoToPayload(payload.getProperties(), headers, true));

        String result = graphDataService.addVertex(version, type, payload);
        response = Response.status(Status.CREATED).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  private JsonElement mergeHeaderInFoToPayload(JsonElement propertiesFromRequest,  HttpHeaders headers, boolean isAdd) {
	    if(!headers.getRequestHeaders().containsKey("X-FromAppId"))  
	        return propertiesFromRequest;
	    
	    String sourceOfTruth = headers.getRequestHeaders().getFirst("X-FromAppId");  
	    Set<Map.Entry<String, JsonElement>> properties = new HashSet<Map.Entry<String, JsonElement>>();
	    properties.addAll(propertiesFromRequest.getAsJsonObject().entrySet());
	    
	    Set<String> propertyKeys = new HashSet<String>();
	    for(Map.Entry<String, JsonElement> property : properties) {
	      propertyKeys.add(property.getKey());
	    }
	    
	    if(!propertyKeys.contains(AAIProperties.LAST_MOD_SOURCE_OF_TRUTH)) {
	        properties.add(new AbstractMap.SimpleEntry<String, JsonElement>(AAIProperties.LAST_MOD_SOURCE_OF_TRUTH,
	            (JsonElement)(new JsonPrimitive(sourceOfTruth))));
	    }
	   
	    if(isAdd && !propertyKeys.contains(AAIProperties.SOURCE_OF_TRUTH)) {
	        properties.add(new AbstractMap.SimpleEntry<String, JsonElement>(AAIProperties.SOURCE_OF_TRUTH,
	            (JsonElement)(new JsonPrimitive(sourceOfTruth))));
	    }

	    Object[] propArray = properties.toArray();
	    StringBuilder sb = new StringBuilder();
	    sb.append("{");
	    boolean first=true;
	    for(int i=0; i<propArray.length; i++) {
	      
	      Map.Entry<String, JsonElement> entry = (Entry<String, JsonElement>) propArray[i];
	      if(!first) {
	        sb.append(",");
	      }
	      sb.append("\"").append(entry.getKey()).append("\"").append(":").append(entry.getValue());
	      first=false;
	    }
	    sb.append("}");
	    
	    return gson.fromJson(sb.toString(), JsonElement.class);
	  }

  private void validateBulkPayload(BulkPayload payload) throws CrudException {
    List<String> vertices = new ArrayList<String>();
    List<String> edges = new ArrayList<String>();

    for (JsonElement v : payload.getObjects()) {
      List<Map.Entry<String, JsonElement>> entries = new ArrayList<Map.Entry<String, JsonElement>>(
          v.getAsJsonObject().entrySet());

      if (entries.size() != 2) {
        throw new CrudException("", Status.BAD_REQUEST);
      }
      Map.Entry<String, JsonElement> opr = entries.get(0);
      Map.Entry<String, JsonElement> item = entries.get(1);

      if (vertices.contains(item.getKey())) {
        throw new CrudException("duplicate vertex in payload: " + item.getKey(), Status.BAD_REQUEST);
      }
      VertexPayload vertexPayload = VertexPayload.fromJson(item.getValue().getAsJsonObject().toString());
      if (vertexPayload.getType() == null) {
        throw new CrudException("Vertex Type cannot be null for: " + item.getKey(), Status.BAD_REQUEST);
      }

      if (!opr.getKey().equalsIgnoreCase("operation")) {
        throw new CrudException("operation missing in item: " + item.getKey(), Status.BAD_REQUEST);
      }

      if (!opr.getValue().getAsString().equalsIgnoreCase("add")
          && !opr.getValue().getAsString().equalsIgnoreCase("modify")
          && !opr.getValue().getAsString().equalsIgnoreCase("delete")) {
        throw new CrudException("Invalid operation at item: " + item.getKey(), Status.BAD_REQUEST);
      }
      // check if ID is populate for modify/delete operation
      if ((opr.getValue().getAsString().equalsIgnoreCase("modify")
          || opr.getValue().getAsString().equalsIgnoreCase("delete")) && (vertexPayload.getId() == null)) {

        throw new CrudException("Mising ID at item: " + item.getKey(), Status.BAD_REQUEST);

      }

      vertices.add(item.getKey());
    }

    for (JsonElement v : payload.getRelationships()) {
      List<Map.Entry<String, JsonElement>> entries = new ArrayList<Map.Entry<String, JsonElement>>(
          v.getAsJsonObject().entrySet());

      if (entries.size() != 2) {
        throw new CrudException("", Status.BAD_REQUEST);
      }
      Map.Entry<String, JsonElement> opr = entries.get(0);
      Map.Entry<String, JsonElement> item = entries.get(1);

      if (edges.contains(item.getKey())) {
        throw new CrudException("duplicate Edge in payload: " + item.getKey(), Status.BAD_REQUEST);
      }

      EdgePayload edgePayload = EdgePayload.fromJson(item.getValue().getAsJsonObject().toString());

      if (edgePayload.getType() == null) {
        throw new CrudException("Edge Type cannot be null for: " + item.getKey(), Status.BAD_REQUEST);
      }

      if (!opr.getKey().equalsIgnoreCase("operation")) {
        throw new CrudException("operation missing in item: " + item.getKey(), Status.BAD_REQUEST);
      }

      if (!opr.getValue().getAsString().equalsIgnoreCase("add")
          && !opr.getValue().getAsString().equalsIgnoreCase("modify")
          && !opr.getValue().getAsString().equalsIgnoreCase("delete")) {
        throw new CrudException("Invalid operation at item: " + item.getKey(), Status.BAD_REQUEST);
      }
      // check if ID is populate for modify/delete operation
      if ((edgePayload.getId() == null) && (opr.getValue().getAsString().equalsIgnoreCase("modify")
          || opr.getValue().getAsString().equalsIgnoreCase("delete"))) {

        throw new CrudException("Mising ID at item: " + item.getKey(), Status.BAD_REQUEST);

      }
      if (opr.getValue().getAsString().equalsIgnoreCase("add")) {
        if (edgePayload.getSource() == null || edgePayload.getTarget() == null) {
          throw new CrudException("Source/Target cannot be null for edge: " + item.getKey(), Status.BAD_REQUEST);
        }
        if (edgePayload.getSource().startsWith("$") && !vertices.contains(edgePayload.getSource().substring(1))) {
          throw new CrudException(
              "Source Vertex " + edgePayload.getSource().substring(1) + " not found for Edge: " + item.getKey(),
              Status.BAD_REQUEST);
        }

        if (edgePayload.getTarget().startsWith("$") && !vertices.contains(edgePayload.getTarget().substring(1))) {
          throw new CrudException(
              "Target Vertex " + edgePayload.getSource().substring(1) + " not found for Edge: " + item.getKey(),
              Status.BAD_REQUEST);
        }
      }
      edges.add(item.getKey());

    }

  }

  @POST
  @Path("/{version}/bulk/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response addBulk(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo uriInfo,
      @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.POST, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        BulkPayload payload = BulkPayload.fromJson(content);
        if ((payload.getObjects() == null && payload.getRelationships() == null)
            || (payload.getObjects() != null && payload.getObjects().isEmpty() && payload.getRelationships() != null
                && payload.getRelationships().isEmpty())) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }

        validateBulkPayload(payload);
        String result = graphDataService.addBulk(version, payload);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @POST
  @Path("/{version}/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response addVertex(String content, @PathParam("version") String version, @PathParam("uri") @Encoded String uri,
      @Context HttpHeaders headers, @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.POST, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {
      try {

        VertexPayload payload = VertexPayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null) {
          throw new CrudException("ID specified , use Http PUT to update Vertex", Status.BAD_REQUEST);
        }

        if (payload.getType() == null || payload.getType().isEmpty()) {
          throw new CrudException("Missing Vertex Type ", Status.BAD_REQUEST);
        }
        String result = graphDataService.addVertex(version, payload.getType(), payload);
        response = Response.status(Status.CREATED).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @POST
  @Path("/relationships/{version}/{type}/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response addEdge(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers, @Context UriInfo uriInfo,
      @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.POST, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        EdgePayload payload = EdgePayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null) {
          throw new CrudException("ID specified , use Http PUT to update Edge", Status.BAD_REQUEST);
        }

        if (payload.getType() != null && !payload.getType().equals(type)) {
          throw new CrudException("Edge Type mismatch", Status.BAD_REQUEST);
        }
        String result = graphDataService.addEdge(version, type, payload);
        response = Response.status(Status.CREATED).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @POST
  @Path("/relationships/{version}/")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response addEdge(String content, @PathParam("version") String version, @PathParam("uri") @Encoded String uri,
      @Context HttpHeaders headers, @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.POST, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        EdgePayload payload = EdgePayload.fromJson(content);
        if (payload.getProperties() == null || payload.getProperties().isJsonNull()) {
          throw new CrudException("Invalid request Payload", Status.BAD_REQUEST);
        }
        if (payload.getId() != null) {
          throw new CrudException("ID specified , use Http PUT to update Edge", Status.BAD_REQUEST);
        }

        if (payload.getType() == null || payload.getType().isEmpty()) {
          throw new CrudException("Missing Edge Type ", Status.BAD_REQUEST);
        }
        String result = graphDataService.addEdge(version, payload.getType(), payload);

        response = Response.status(Status.CREATED).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @DELETE
  @Path("/{version}/{type}/{id}")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response deleteVertex(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;

    if (validateRequest(req, uri, content, Action.DELETE, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        String result = graphDataService.deleteVertex(version, id, type);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  @DELETE
  @Path("/relationships/{version}/{type}/{id}")
  @Consumes({ MediaType.APPLICATION_JSON })
  @Produces({ MediaType.APPLICATION_JSON })
  public Response deleteEdge(String content, @PathParam("version") String version, @PathParam("type") String type,
      @PathParam("id") String id, @PathParam("uri") @Encoded String uri, @Context HttpHeaders headers,
      @Context UriInfo uriInfo, @Context HttpServletRequest req) {

    LoggingUtil.initMdcContext(req, headers);

    logger.debug("Incoming request..." + content);
    Response response = null;
    if (validateRequest(req, uri, content, Action.DELETE, CrudServiceConstants.CRD_AUTH_POLICY_NAME)) {

      try {
        String result = graphDataService.deleteEdge(version, id, type);
        response = Response.status(Status.OK).entity(result).type(mediaType).build();
      } catch (CrudException ce) {
        response = Response.status(ce.getHttpStatus()).entity(ce.getMessage()).build();
      } catch (Exception e) {
        response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
      }
    } else {
      response = Response.status(Status.FORBIDDEN).entity(content).type(MediaType.APPLICATION_JSON).build();
    }

    LoggingUtil.logRestRequest(logger, auditLogger, req, response);
    return response;
  }

  protected boolean validateRequest(HttpServletRequest req, String uri, String content, Action action,
      String authPolicyFunctionName) {
    try {
      String cipherSuite = (String) req.getAttribute("javax.servlet.request.cipher_suite");
      String authUser = null;
      if (cipherSuite != null) {
        X509Certificate[] certChain = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
        X509Certificate clientCert = certChain[0];
        X500Principal subjectDn = clientCert.getSubjectX500Principal();
        authUser = subjectDn.toString();
      }
      return this.auth.validateRequest(authUser.toLowerCase(), action.toString() + ":" + authPolicyFunctionName);
    } catch (Exception e) {
      logResult(action, uri, e);
      return false;
    }
  }

  void logResult(Action op, String uri, Exception e) {

    logger.error(CrudServiceMsgs.EXCEPTION_DURING_METHOD_CALL, op.toString(), uri, e.getStackTrace().toString());

    // Clear the MDC context so that no other transaction inadvertently
    // uses our transaction id.
    MDC.clear();
  }
}
