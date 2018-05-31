/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.authorization.client.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.authorization.client.util.Throwables;
import org.keycloak.authorization.client.util.TokenCallable;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.util.JsonSerialization;

/**
 * An entry point for managing resources using the Protection API.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class ProtectedResource {

    private final Http http;
    private ServerConfiguration serverConfiguration;
    private final Configuration configuration;
    private final TokenCallable pat;

    ProtectedResource(Http http, ServerConfiguration serverConfiguration, Configuration configuration, TokenCallable pat) {
        this.http = http;
        this.serverConfiguration = serverConfiguration;
        this.configuration = configuration;
        this.pat = pat;
    }

    /**
     * Creates a new resource.
     *
     * @param resource the resource data
     * @return a {@link RegistrationResponse}
     */
    public ResourceRepresentation create(final ResourceRepresentation resource) {
        Callable<ResourceRepresentation> callable = new Callable<ResourceRepresentation>() {
            @Override
            public ResourceRepresentation call() throws Exception {
                return http.<ResourceRepresentation>post(serverConfiguration.getResourceRegistrationEndpoint())
                        .authorizationBearer(pat.call())
                        .json(JsonSerialization.writeValueAsBytes(resource))
                        .response().json(ResourceRepresentation.class).execute();
            }
        };
        try {
            return callable.call();
        } catch (Exception cause) {
            return Throwables.retryAndWrapExceptionIfNecessary(callable, pat, "Could not create resource", cause);
        }
    }

    /**
     * Updates a resource.
     *
     * @param resource the resource data
     * @return a {@link RegistrationResponse}
     */
    public void update(final ResourceRepresentation resource) {
        if (resource.getId() == null) {
            throw new IllegalArgumentException("You must provide the resource id");
        }

        Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                http.<ResourceRepresentation>put(serverConfiguration.getResourceRegistrationEndpoint() + "/" + resource.getId())
                        .authorizationBearer(pat.call())
                        .json(JsonSerialization.writeValueAsBytes(resource)).execute();
                return null;
            }
        };
        try {
            callable.call();
        } catch (Exception cause) {
            Throwables.retryAndWrapExceptionIfNecessary(callable, pat, "Could not update resource", cause);
        }
    }

    /**
     * Query the server for a resource given its <code>id</code>.
     *
     * @param id the resource id
     * @return a {@link ResourceRepresentation}
     */
    public ResourceRepresentation findById(final String id) {
        Callable<ResourceRepresentation> callable = new Callable<ResourceRepresentation>() {
            @Override
            public ResourceRepresentation call() throws Exception {
                return http.<ResourceRepresentation>get(serverConfiguration.getResourceRegistrationEndpoint() + "/" + id)
                        .authorizationBearer(pat.call())
                        .response().json(ResourceRepresentation.class).execute();
            }
        };
        try {
            return callable.call();
        } catch (Exception cause) {
            return Throwables.retryAndWrapExceptionIfNecessary(callable, pat, "Could not find resource", cause);
        }
    }

    /**
     * Query the server for a resource given its <code>name</code> where the owner is the resource server itself.
     *
     * @param name the resource name
     * @return a {@link ResourceRepresentation}
     */
    public ResourceRepresentation findByName(String name) {
        String[] representations = find(null, name, null, configuration.getResource(), null, null, false, null, null);

        if (representations.length == 0) {
            return null;
        }

        return findById(representations[0]);
    }

    /**
     * Query the server for a resource given its <code>name</code> and a given <code>ownerId</code>.
     *
     * @param name the resource name
     * @param ownerId the owner id
     * @return a {@link ResourceRepresentation}
     */
    public ResourceRepresentation findByName(String name, String ownerId) {
        String[] representations = find(null, name, null, ownerId, null, null, false, null, null);

        if (representations.length == 0) {
            return null;
        }

        return findById(representations[0]);
    }

    /**
     * Query the server for any resource with the matching arguments.
     *
     * @param id the resource id
     * @param name the resource name
     * @param uri the resource uri
     * @param owner the resource owner
     * @param type the resource type
     * @param scope the resource scope
     * @param matchingUri the resource uri. Use this parameter to lookup a resource that best match the given uri
     * @param firstResult the position of the first resource to retrieve
     * @param maxResult the maximum number of resources to retrieve
     * @return an array of strings with the resource ids
     */
    public String[] find(final String id, final String name, final String uri, final String owner, final String type, final String scope, final boolean matchingUri, final Integer firstResult, final Integer maxResult) {
        Callable<String[]> callable = new Callable<String[]>() {
            @Override
            public String[] call() throws Exception {
                return http.<String[]>get(serverConfiguration.getResourceRegistrationEndpoint())
                        .authorizationBearer(pat.call())
                        .param("_id", id)
                        .param("name", name)
                        .param("uri", uri)
                        .param("owner", owner)
                        .param("type", type)
                        .param("scope", scope)
                        .param("matchingUri", Boolean.valueOf(matchingUri).toString())
                        .param("deep", Boolean.FALSE.toString())
                        .param("first", firstResult != null ? firstResult.toString() : null)
                        .param("max", maxResult != null ? maxResult.toString() : null)
                        .response().json(String[].class).execute();
            }
        };
        try {
            return callable.call();
        } catch (Exception cause) {
            return Throwables.retryAndWrapExceptionIfNecessary(callable, pat, "Could not find resource", cause);
        }
    }

    /**
     * Query the server for all resources.
     *
     * @return @return an array of strings with the resource ids
     */
    public String[] findAll() {
        try {
            return find(null,null , null, null, null, null, false, null, null);
        } catch (Exception cause) {
            throw Throwables.handleWrapException("Could not find resource", cause);
        }
    }

    /**
     * Deletes a resource with the given <code>id</code>.
     *
     * @param id the resource id
     */
    public void delete(final String id) {
        Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                http.delete(serverConfiguration.getResourceRegistrationEndpoint() + "/" + id)
                        .authorizationBearer(pat.call())
                        .execute();
                return null;
            }
        };
        try {
            callable.call();
        } catch (Exception cause) {
            Throwables.retryAndWrapExceptionIfNecessary(callable, pat, "", cause);
        }
    }

    /**
     * Query the server for all resources with the given uri.
     *
     * @param uri the resource uri
     */
    public List<ResourceRepresentation> findByUri(String uri) {
        String[] ids = find(null, null, uri, null, null, null, false, null, null);

        if (ids.length == 0) {
            return Collections.emptyList();
        }

        List<ResourceRepresentation> representations = new ArrayList<>();

        for (String id : ids) {
            representations.add(findById(id));
        }

        return representations;
    }

    /**
     * Returns a list of resources that best matches the given {@code uri}. This method queries the server for resources whose
     * {@link ResourceRepresentation#uri} best matches the given {@code uri}.
     *
     * @param uri the resource uri to match
     * @return a list of resources
     */
    public List<ResourceRepresentation> findByMatchingUri(String uri) {
        String[] ids = find(null, null, uri, null, null, null, true, null, null);

        if (ids.length == 0) {
            return Collections.emptyList();
        }

        List<ResourceRepresentation> representations = new ArrayList<>();

        for (String id : ids) {
            representations.add(findById(id));
        }

        return representations;
    }
}