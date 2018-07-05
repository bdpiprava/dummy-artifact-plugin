/*
 * Copyright 2018 ThoughtWorks, Inc.
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

package cd.go.artifact.dummy;

import cd.go.artifact.dummy.model.ArtifactConfig;
import cd.go.artifact.dummy.model.ArtifactStore;
import cd.go.artifact.dummy.model.FetchArtifact;
import cd.go.artifact.dummy.request.FetchArtifactRequest;
import cd.go.artifact.dummy.request.PublishArtifactRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import okhttp3.*;

import java.io.IOException;
import java.util.Base64;

import static cd.go.artifact.dummy.model.ArtifactStore.artifactStoreMetadata;
import static java.util.Collections.singletonList;

@Extension
public class DummyArtifactPlugin implements GoPlugin {
    public static final Gson GSON = new Gson();
    public static final Logger LOG = Logger.getLoggerFor(DummyArtifactPlugin.class);
    private GoApplicationAccessor goApplicationAccessor;
    public static final OkHttpClient CLIENT = new OkHttpClient();
    public static final MediaType MEDIA_TYPE_BLOB = MediaType.parse("text/x-markdown; charset=utf-8");

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        this.goApplicationAccessor = goApplicationAccessor;
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException {
        final RequestFromServer requestFromServer = RequestFromServer.from(request.requestName());
        try {
            switch (requestFromServer) {
                case REQUEST_GET_CAPABILITIES:
                    return DefaultGoPluginApiResponse.success("{}");
                case REQUEST_STORE_CONFIG_METADATA:
                    return DefaultGoPluginApiResponse.success(artifactStoreMetadata());
                case REQUEST_STORE_CONFIG_VIEW:
                    return DefaultGoPluginApiResponse.success(new View("/artifact-store.template.html").toJSON());
                case REQUEST_STORE_CONFIG_VALIDATE:
                    return DefaultGoPluginApiResponse.success(ArtifactStore.from(request.requestBody()).validate().toJSON());
                case REQUEST_PUBLISH_ARTIFACT_METADATA:
                    return DefaultGoPluginApiResponse.success(ArtifactConfig.artifactConfigMetadata());
                case REQUEST_PUBLISH_ARTIFACT_VIEW:
                    return DefaultGoPluginApiResponse.success(new View("/publish-artifact.template.html").toJSON());
                case REQUEST_PUBLISH_ARTIFACT_VALIDATE:
                    return DefaultGoPluginApiResponse.success(ArtifactConfig.from(request.requestBody()).validate().toJSON());
                case REQUEST_FETCH_ARTIFACT_METADATA:
                    return DefaultGoPluginApiResponse.success(FetchArtifact.metadata());
                case REQUEST_FETCH_ARTIFACT_VIEW:
                    return DefaultGoPluginApiResponse.success(new View("/fetch-artifact.template.html").toJSON());
                case REQUEST_FETCH_ARTIFACT_VALIDATE:
                    return DefaultGoPluginApiResponse.success(FetchArtifact.from(request.requestBody()).validate().toJSON());
                case REQUEST_PUBLISH_ARTIFACT:
                    return publishArtifact(PublishArtifactRequest.fromJSON(request.requestBody()));
                case REQUEST_FETCH_ARTIFACT:
                    return fetchArtifact(FetchArtifactRequest.fromJSON(request.requestBody()));
                case REQUEST_GET_PLUGIN_ICON:
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("content_type", "image/jpg");
                    jsonObject.addProperty("data", Base64.getEncoder().encodeToString(ResourceReader.readBytes("/icon.jpg")));
                    return DefaultGoPluginApiResponse.success(jsonObject.toString());
                default:
                    throw new RuntimeException("Error while executing request" + request.requestName());
            }
        } catch (Exception e) {
            LOG.error("Error while executing request " + request.requestName(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier("artifact", singletonList("1.0"));
    }

    private GoPluginApiResponse fetchArtifact(FetchArtifactRequest request) {
        return null;
    }

    private GoPluginApiResponse publishArtifact(PublishArtifactRequest publishArtifactRequest) throws IOException {
        ArtifactConfig artifactConfig = publishArtifactRequest.getArtifactConfig();
        ArtifactStore artifactStore = publishArtifactRequest.getArtifactStore();
        String pipeline = publishArtifactRequest.getEnvironmentVariables().get("GO_PIPELINE_NAME");
        String pipelineCounter = publishArtifactRequest.getEnvironmentVariables().get("GO_PIPELINE_COUNTER");
        String stage = publishArtifactRequest.getEnvironmentVariables().get("GO_STAGE_NAME");
        String stageCounter = publishArtifactRequest.getEnvironmentVariables().get("GO_STAGE_COUNTER");
        String job = publishArtifactRequest.getEnvironmentVariables().get("GO_JOB_NAME");

        RequestBody body = RequestBody.create(MediaType.parse("application/java-archive"), artifactConfig.getSource());

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", artifactConfig.getSource(), body)
                .build();

        HttpUrl httpUrl = HttpUrl.parse(artifactStore.getUrl())
                .newBuilder()
                .addPathSegment("files")
                .addPathSegment(pipeline)
                .addPathSegment(pipelineCounter)
                .addPathSegment(stage)
                .addPathSegment(stageCounter)
                .addPathSegment(job)
                .addPathSegment(artifactConfig.getDestination())
                .addPathSegment(artifactConfig.getSource())
                .build();


        Request request = new Request.Builder()
                .url(httpUrl)
                .post(requestBody)
                .build();

        Response response = CLIENT.newCall(request).execute();
        if (response.isSuccessful()) {
            return DefaultGoPluginApiResponse.success("{}");
        }

        return DefaultGoPluginApiResponse.error(response.body().string());
    }
}
