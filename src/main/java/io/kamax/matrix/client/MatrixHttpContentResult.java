/*
 * matrix-java-sdk - Matrix Client SDK for Java
 * Copyright (C) 2017 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.matrix.client;

import org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.util.*;

import okhttp3.Headers;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MatrixHttpContentResult {
    private final boolean valid;
    private final Headers headers;
    private final Optional<String> contentType;
    private final byte[] data;

    public MatrixHttpContentResult(Response response) throws IOException {
        ResponseBody entity = response.body();
        valid = entity != null && response.code() == 200;

        if (entity != null) {
            headers = response.headers();
            String contentTypeHeader = entity.contentType().toString();
            if (contentTypeHeader != null) {
                contentType = Optional.of(contentTypeHeader);
            } else {
                contentType = Optional.empty();
            }
            data = IOUtils.toByteArray(entity.byteStream());
        } else {
            headers = new Headers.Builder().build();
            contentType = Optional.empty();
            data = new byte[0];
        }
    }

    public boolean isValid() {
        return valid;
    }

    public Optional<String> getHeader(String name) {
        String header = headers.get(name);
        if (header != null) {
            return Optional.of(header);
        }
        return Optional.empty();
    }

    public Optional<String> getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}
