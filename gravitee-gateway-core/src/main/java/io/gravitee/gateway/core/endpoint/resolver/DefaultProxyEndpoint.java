/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.core.endpoint.resolver;

import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.endpoint.Endpoint;
import io.gravitee.gateway.api.proxy.ProxyRequest;
import io.gravitee.gateway.api.proxy.builder.ProxyRequestBuilder;

import java.util.function.Function;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
class DefaultProxyEndpoint extends AbstractProxyEndpoint {

    DefaultProxyEndpoint(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public ProxyRequest createProxyRequest(Request request, Function<ProxyRequestBuilder, ProxyRequestBuilder> mapper) {
        ProxyRequestBuilder builder = ProxyRequestBuilder.from(request)
                .uri(target() + request.pathInfo());

        if (mapper != null) {
            builder = mapper.apply(builder);
        }

        return builder.build();
    }
}
