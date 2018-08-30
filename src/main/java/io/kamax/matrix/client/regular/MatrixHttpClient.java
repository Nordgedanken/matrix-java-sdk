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

package io.kamax.matrix.client.regular;

import com.google.gson.JsonObject;

import io.kamax.matrix.MatrixID;
import io.kamax.matrix._MatrixContent;
import io.kamax.matrix._MatrixID;
import io.kamax.matrix._MatrixUser;
import io.kamax.matrix.client.*;
import io.kamax.matrix.hs._MatrixRoom;
import io.kamax.matrix.json.*;
import io.kamax.matrix.room.RoomAlias;
import io.kamax.matrix.room.RoomAliasLookup;
import io.kamax.matrix.room._RoomAliasLookup;
import io.kamax.matrix.room._RoomCreationOptions;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MatrixHttpClient extends AMatrixHttpClient implements _MatrixClient {

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public MatrixHttpClient(String domain) {
        super(domain);
    }

    public MatrixHttpClient(MatrixClientContext context) {
        super(context);
    }

    public MatrixHttpClient(MatrixClientContext context, MatrixClientDefaults defaults) {
        super(context, defaults);
    }

    public MatrixHttpClient(MatrixClientContext context, OkHttpClient client) {
        super(context, client);
    }

    protected _MatrixID getMatrixId(String localpart) {
        return new MatrixID(localpart, getHomeserver().getDomain());
    }

    @Override
    protected HttpUrl getClientPathBuilder(String action) {
        HttpUrl builder = super.getClientPathBuilder(action);
        if (context.getUser().isPresent()) {
            _MatrixID user = context.getUser().get();
            builder = HttpUrl.parse(builder.encodedPath().replace("{userId}", user.getId()));
        }
        return builder;
    }

    @Override
    public _MatrixID getWhoAmI() {
        HttpUrl path = getClientPathWithAccessToken("/account/whoami");
        Request request = new Request.Builder()
                .url(path)
                .build();
        String body = execute(request);
        return MatrixID.from(GsonUtil.getStringOrThrow(GsonUtil.parseObj(body), "user_id")).acceptable();
    }

    @Override
    public void setDisplayName(String name) {
        HttpUrl path = getClientPathWithAccessToken("/profile/{userId}/displayname");
        RequestBody body = RequestBody.create(JSON, gson.toJson(new UserDisplaynameSetBody(name)));
        Request request = new Request.Builder()
                .url(path)
                .put(body)
                .build();
        execute(request);
    }

    @Override
    public _RoomAliasLookup lookup(RoomAlias alias) {
        HttpUrl path = getClientPath("/directory/room/" + alias.getId());
        Request request = new Request.Builder()
                .url(path)
                .build();
        String resBody = execute(request);
        RoomAliasLookupJson lookup = GsonUtil.get().fromJson(resBody, RoomAliasLookupJson.class);
        return new RoomAliasLookup(lookup.getRoomId(), alias.getId(), lookup.getServers());
    }

    @Override
    public _MatrixRoom createRoom(_RoomCreationOptions options) {
        HttpUrl path = getClientPathWithAccessToken("/createRoom");
        RequestBody body = RequestBody.create(JSON, gson.toJson(new RoomCreationRequestJson(options)));
        Request request = new Request.Builder()
                .url(path)
                .post(body)
                .build();

        String resBody = execute(request);
        String roomId = GsonUtil.get().fromJson(resBody, RoomCreationResponseJson.class).getRoomId();
        return getRoom(roomId);
    }

    @Override
    public _MatrixRoom getRoom(String roomId) {
        return new MatrixHttpRoom(getContext(), roomId);
    }

    @Override
    public List<_MatrixRoom> getJoinedRooms() {
        HttpUrl path = getClientPathWithAccessToken("/joined_rooms");
        Request request = new Request.Builder()
                .url(path)
                .build();
        JsonObject resBody = GsonUtil.parseObj(execute(request));
        return GsonUtil.asList(resBody, "joined_rooms", String.class).stream().map(this::getRoom)
                .collect(Collectors.toList());
    }

    @Override
    public _MatrixRoom joinRoom(String roomIdOrAlias) {
        HttpUrl path = getClientPathWithAccessToken("/join/" + roomIdOrAlias);
        RequestBody body = RequestBody.create(JSON, gson.toJson(new JsonObject()));
        Request request = new Request.Builder()
                .url(path)
                .post(body)
                .build();

        String resBody = execute(request);
        String roomId = GsonUtil.get().fromJson(resBody, RoomCreationResponseJson.class).getRoomId();
        return getRoom(roomId);
    }

    @Override
    public _MatrixUser getUser(_MatrixID mxId) {
        return new MatrixHttpUser(getContext(), mxId);
    }

    @Override
    public Optional<String> getDeviceId() {
        return Optional.ofNullable(context.getDeviceId());
    }

    protected void updateContext(String resBody) {
        LoginResponse response = gson.fromJson(resBody, LoginResponse.class);
        context.setToken(response.getAccessToken());
        context.setDeviceId(response.getDeviceId());
        //context.setUser(MatrixID.asValid(response.getUserId()));
        context.setUser(MatrixID.asAcceptable(response.getUserId()));
    }

    @Override
    public void register(MatrixPasswordCredentials credentials, String sharedSecret, boolean admin) {
        // As per synapse registration script:
        // https://github.com/matrix-org/synapse/blob/master/scripts/register_new_matrix_user#L28
        String value = credentials.getLocalPart() + "\0" + credentials.getPassword() + "\0"
                + (admin ? "admin" : "notadmin");
        String mac = new HmacUtils(HmacAlgorithms.HMAC_SHA_1, sharedSecret).hmacHex(value);
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("user", credentials.getLocalPart());
        bodyJson.addProperty("password", credentials.getPassword());
        bodyJson.addProperty("mac", mac);
        bodyJson.addProperty("type", "org.matrix.login.shared_secret");
        bodyJson.addProperty("admin", false);

        RequestBody body = RequestBody.create(JSON, gson.toJson(bodyJson));
        Request request = new Request.Builder()
                .url(getPath("client", "api/v1", "/register"))
                .post(body)
                .build();
        updateContext(execute(request));
    }

    @Override
    public void login(MatrixPasswordCredentials credentials) {
        LoginPostBody data = new LoginPostBody(credentials.getLocalPart(), credentials.getPassword());
        getDeviceId().ifPresent(data::setDeviceId);
        Optional.ofNullable(context.getInitialDeviceName()).ifPresent(data::setInitialDeviceDisplayName);

        RequestBody body = RequestBody.create(JSON, gson.toJson(data));
        Request request = new Request.Builder()
                .url(getClientPath("/login"))
                .post(body)
                .build();
        updateContext(execute(request));
    }

    @Override
    public void logout() {
        HttpUrl path = getClientPathWithAccessToken("/logout");
        RequestBody body = RequestBody.create(JSON, gson.toJson("{}"));
        Request request = new Request.Builder()
                .url(path)
                .post(body)
                .build();
        execute(request);
        context.setToken(null);
        context.setUser(null);
        context.setDeviceId(null);
    }

    @Override
    public _SyncData sync(_SyncOptions options) {
        HttpUrl path = getClientPathBuilder("/sync");
        HttpUrl.Builder builder = path.newBuilder();

        builder.addQueryParameter("timeout", options.getTimeout().map(Long::intValue).orElse(30000).toString());
        options.getSince().ifPresent(since -> builder.addQueryParameter("since", since));
        options.getFilter().ifPresent(filter -> builder.addQueryParameter("filter", filter));
        options.withFullState().ifPresent(state -> builder.addQueryParameter("full_state", state ? "true" : "false"));
        options.getSetPresence().ifPresent(presence -> builder.addQueryParameter("presence", presence));

        Request request = new Request.Builder()
                .url(getWithAccessToken(builder.build()))
                .build();
        String body = execute(request);
        return new SyncDataJson(GsonUtil.parseObj(body));
    }

    @Override
    public _MatrixContent getMedia(String mxUri) throws IllegalArgumentException {
        return getMedia(URI.create(mxUri));
    }

    @Override
    public _MatrixContent getMedia(URI mxUri) throws IllegalArgumentException {
        return new MatrixHttpContent(context, mxUri);
    }

}
