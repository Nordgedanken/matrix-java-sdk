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

import io.kamax.matrix._MatrixContent;
import io.kamax.matrix._MatrixID;
import io.kamax.matrix._MatrixUser;
import io.kamax.matrix.client.regular.Presence;
import io.kamax.matrix.json.GsonUtil;
import okhttp3.HttpUrl;
import okhttp3.Request;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class MatrixHttpUser extends AMatrixHttpClient implements _MatrixUser {

    private Logger log = LoggerFactory.getLogger(MatrixHttpUser.class);

    private _MatrixID mxId;

    public MatrixHttpUser(MatrixClientContext context, _MatrixID mxId) {
        super(context);

        this.mxId = mxId;
    }

    @Override
    public _MatrixID getId() {
        return mxId;
    }

    @Override
    public Optional<String> getName() {
        HttpUrl path = getClientPathWithAccessToken("/profile/" + mxId.getId() + "/displayname");
        Request req = new Request.Builder()
                .url(path)
                .build();


        MatrixHttpRequest request = new MatrixHttpRequest(req);
        request.addIgnoredErrorCode(404);
        String body = execute(request);
        return extractAsStringFromBody(body, "displayname");
    }

    @Override
    public Optional<String> getAvatarUrl() {
        HttpUrl path = getClientPathWithAccessToken("/profile/" + mxId.getId() + "/avatar_url");
        Request req = new Request.Builder()
                .url(path)
                .build();
        MatrixHttpRequest request = new MatrixHttpRequest(req);
        request.addIgnoredErrorCode(404);
        String body = execute(request);
        return extractAsStringFromBody(body, "avatar_url");
    }

    @Override
    public Optional<_MatrixContent> getAvatar() {
        return getAvatarUrl().flatMap(uri -> {
            try {
                return Optional.of(new MatrixHttpContent(getContext(), new URI(uri)));
            } catch (URISyntaxException e) {
                log.debug("{} is not a valid URI for avatar, returning empty", uri);
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<_Presence> getPresence() {
        HttpUrl path = getClientPathWithAccessToken("/presence/" + mxId.getId() + "/status");
        Request req = new Request.Builder()
                .url(path)
                .build();

        MatrixHttpRequest request = new MatrixHttpRequest(req);
        request.addIgnoredErrorCode(404);
        String body = execute(request);
        if (StringUtils.isBlank(body)) {
            return Optional.empty();
        }

        return Optional.of(new Presence(GsonUtil.parseObj(body)));
    }

}
