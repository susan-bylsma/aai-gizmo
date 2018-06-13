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
package org.onap.schema;

import com.google.common.collect.Multimap;
import org.codehaus.jackson.map.ObjectMapper;
import org.onap.aai.edges.EdgeRule;
import org.onap.crud.exception.CrudException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response.Status;


public class RelationshipSchema {

  public static final String SCHEMA_RELATIONSHIP_TYPE = "label";


  private Map<String, Map<String, Class<?>>> relations = new HashMap<>();
  /**
   * Hashmap of valid relationship types along with properties.
   */
  private Map<String, Map<String, Class<?>>> relationTypes  = new HashMap<>();

  public RelationshipSchema( Multimap<String, EdgeRule> rules, String props) throws CrudException, IOException {
    HashMap<String, String> properties = new ObjectMapper().readValue(props, HashMap.class);
    Map<String, Class<?>> edgeProps = properties.entrySet().stream().collect(Collectors.toMap(p -> p.getKey(), p -> {
      try {
        return resolveClass(p.getValue());
      } catch (CrudException | ClassNotFoundException e) {
        e.printStackTrace();
      }
      return null;
    }));

    rules.entries ().forEach ( (kv) -> {
      relationTypes.put(kv.getValue ().getLabel (), edgeProps);
      relations.put (buildRelation ( kv.getValue ().getFrom (), kv.getValue ().getTo (), kv.getValue ().getLabel ()), edgeProps);
    });
  }

  public Map<String, Class<?>> lookupRelation(String key) {
    return this.relations.get(key);
  }

  public Map<String, Class<?>> lookupRelationType(String type) {
    return this.relationTypes.get(type);
  }

  public boolean isValidType(String type) {
    return relationTypes.containsKey(type);
  }


  private String buildRelation(String source, String target, String relation){
    return source + ":" + target + ":" + relation;
  }

  private Class<?> resolveClass(String type) throws CrudException, ClassNotFoundException {
    Class<?> clazz = Class.forName(type);
    validateClassTypes(clazz);
    return clazz;
  }

  private void validateClassTypes(Class<?> clazz) throws CrudException {
    if (!clazz.isAssignableFrom(Integer.class) && !clazz.isAssignableFrom(Double.class)
            && !clazz.isAssignableFrom(Boolean.class) && !clazz.isAssignableFrom(String.class)) {
      throw new CrudException("", Status.BAD_REQUEST);
    }
  }
}


