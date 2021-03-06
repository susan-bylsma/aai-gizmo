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
package org.onap.crud.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.internal.helper.DatabaseField;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.eclipse.persistence.mappings.DatabaseMapping;
import org.json.JSONArray;
import org.json.JSONObject;
import org.onap.crud.exception.CrudException;
import org.onap.crud.util.CrudServiceUtil;
import org.onap.schema.OxmModelLoader;
import org.onap.schema.OxmModelValidator;

import com.google.common.base.CaseFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.dongliu.gson.GsonJava8TypeAdapterFactory;

public class Vertex {
  private static final Gson gson = new GsonBuilder().registerTypeAdapterFactory(new GsonJava8TypeAdapterFactory())
      .create();

  @SerializedName(value = "id", alternate = { "key" })
  private final Optional<String> id;

  private final String type;
  private final Map<String, Object> properties;

  private Vertex(Builder builder) {
    this.id = builder.id;
    this.type = builder.type;
    this.properties = builder.properties;
  }

  public static class Builder {
    private Optional<String> id = Optional.empty();
    private final String type;
    private final Map<String, Object> properties = new HashMap<String, Object>();

    public Builder(String type) {
      if (type == null) {
        throw new IllegalArgumentException("Type cannot be null");
      }
      this.type = type;
    }

    public Builder id(String id) {
      if (id == null) {
        throw new IllegalArgumentException("id cannot be null");
      }

      this.id = Optional.of(id);
      return this;
    }

    public Builder property(String key, Object value) {
      if (key == null || value == null) {
        throw new IllegalArgumentException("Property key/value cannot be null");
      }
      properties.put(key, value);
      return this;
    }

    public Vertex build() {
      return new Vertex(this);
    }
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public String toJson(Gson customGson) {
    return customGson.toJson(this);
  }

  public static Vertex fromJson(String jsonString, String version) throws CrudException {
    JSONObject doc = new JSONObject(jsonString);
    return fromJson(doc, version);
  }

  public static Vertex fromJson(JSONObject jsonObject, String version) throws CrudException {
    Builder builder;

    try {
      String type = jsonObject.getString("type");
      builder = new Builder(type).id(jsonObject.getString("key"));

      type = OxmModelValidator.resolveCollectionType(version, type);
      DynamicJAXBContext jaxbContext = OxmModelLoader.getContextForVersion(version);
      
      final DynamicType modelObjectType = OxmModelLoader.getDynamicTypeForVersion(version, type);
      final DynamicType reservedType = jaxbContext.getDynamicType("ReservedPropNames");


      if (modelObjectType == null) {
        throw new CrudException("Unable to load oxm version", javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
      }

      if (jsonObject.has("properties")) {
        JSONObject jsonProps = jsonObject.getJSONObject("properties");
        for (String key : jsonProps.keySet()) {
          String keyJavaName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, key);
          DatabaseMapping mapping = modelObjectType.getDescriptor().getMappingForAttributeName(keyJavaName);

          if (mapping == null) {
            // This might be one of the reserved properties
            mapping = reservedType.getDescriptor().getMappingForAttributeName(keyJavaName);
          }

          if (mapping != null) {
            DatabaseField field = mapping.getField();
            Object value = CrudServiceUtil.validateFieldType(jsonProps.get(key).toString(), field.getType());
            builder.property(key, value);
          }
        }
      }
    }
    catch (Exception ex) {
      throw new CrudException("Unable to transform response: " + jsonObject.toString(), javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
    }

    return builder.build();
  }

  public static List<Vertex> collectionFromJson(String jsonString, String version) throws CrudException {
    List<Vertex> result = new ArrayList<>();
    JSONArray array = new JSONArray(jsonString);

    for (Object jsonObject : array) {
      result.add(Vertex.fromJson((JSONObject)jsonObject, version));
    }

    return result;
  }

  @Override
  public String toString() {
    return "Vertex [id=" + id + ", type=" + type + ", properties=" + properties + "]";
  }

  public Optional<String> getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

}
