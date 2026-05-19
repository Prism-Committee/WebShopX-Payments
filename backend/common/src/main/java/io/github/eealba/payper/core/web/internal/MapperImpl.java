/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.eealba.payper.core.web.internal;

import io.github.eealba.payper.core.web.Headers;
import io.github.eealba.payper.core.web.Request;
import okhttp3.MediaType;
import okhttp3.RequestBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: Edgar Alba
 */
class MapperImpl implements Mapper {
    private static final Mapper INSTANCE = new MapperImpl();

    private MapperImpl() {
    }

    static Mapper getInstance() {
        return INSTANCE;
    }


    @Override
    public okhttp3.Request mapRequest(Request request) {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(request.uri());

        //request.timeout().ifPresent(builder::timeout);
        RequestBody body = null;
        switch (request.method()) {
            case POST:
            case PUT:
            case PATCH:
                if (request.bodyPublisher().isPresent()) {
                    body = RequestBody.create(
                            MediaType.get(request.headers().contentType().orElse("application/octet-stream")),
                            request.bodyPublisher().get().get()
                    );
                }
                break;
            default:
                break;
        }
        builder.method(request.method().name(), body);

        String[] headersArray = request.headers().toArray();
        if (headersArray.length > 0) {
            builder.headers(okhttp3.Headers.of(headersArray));
        }

        return builder.build();
    }

    @Override
    public Headers mapHeaders(okhttp3.Headers headers) {
        Map<String, String> headers2 = new HashMap<>();
        headers.names().forEach((key) -> headers2.put(key, headers.get(key)));
        return new Headers(headers2);
    }
}
