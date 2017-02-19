package fr.redfroggy.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Mojo(name="stack-deploy")
@SuppressWarnings("unchecked")
public class RancherApiMojo extends AbstractMojo {

    private static final Logger logger = LoggerFactory.getLogger(RancherApiMojo.class);

    private RestTemplate restTemplate;

    public RancherApiMojo() {
        restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
    }

    /**
     * Rancher app key
     */
    @Parameter(property="rancher.accessKey", required = true)
    private String accessKey;

    /**
     * Rancher password
     *
     */
    @Parameter(property="rancher.password", required = true)
    private String password;

    /**
     * Rancher url
     */
    @Parameter(property="rancher.url", required = true)
    private String url;

    /**
     * Envrionnment
     */
    @Parameter(property="rancher.environment", required = true)
    private String environment;

    /**
     * Stack name
     */
    @Parameter(property="rancher.stack.name", required = true)
    private String name;

    /**
     * Stack description
     */
    @Parameter(property="rancher.stack.description", required = true)
    private String description;

    /**
     * Stack start on create
     */
    @Parameter(property="rancher.stack.startOnCreate", defaultValue = "true")
    private String startOnCreate;

    /**
     * Location of the docker composer file.
     */
    @Parameter(property="rancher.stack.dockerComposeFilePath", required = true)
    private File dockerComposeFile;

    /**
     * Location of the rancher composer file.
     */
    @Parameter(property="rancher.stack.rancherComposeFilePath")
    private File rancherComposeFile;

    /**
     * Create basic auth header
     * @return HttpHeaders instance
     */
    private HttpHeaders createBasicAuthHeaders(){
        HttpHeaders headers = new HttpHeaders();

        String auth = accessKey + ":" + password;

        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes());
        String authHeader = "Basic " + new String( encodedAuth );

        headers.set("Authorization", authHeader);

        return headers;
    }

    /**
     * Create a new HttpEntity with a basic auth header
     * {@link #createBasicAuthHeaders()}
     * @return HttpEntity instance
     */
    private HttpEntity createEntity() {
        return new HttpEntity(createBasicAuthHeaders());
    }

    /**
     * Get an environment (project) by name
     * @return the api response body (should be json typed)
     */
    private String getEnvironment() {

        String envUrl = url+"/projects?name="+environment;

        ResponseEntity<String> responseEntity = restTemplate.exchange(envUrl, HttpMethod.GET, createEntity(), String.class);

        String responseBody =  responseEntity.getBody();
        Assert.notNull(responseBody, "No http response body for url: "+envUrl);
        Assert.isTrue(!responseBody.isEmpty(), "No http response body for url: "+envUrl);

        return responseBody;
    }

    /**
     *
     * Get a stack by name
     * @return the stack url
     */
    private String getStack() {
        try {
            String environmentResponse = getEnvironment();

            ReadContext ctx = JsonPath.parse(environmentResponse);

            try {
                String stacksUrl = ctx.read("data[0].links.stacks");

                ResponseEntity<String> responseEntity = restTemplate.exchange(stacksUrl + "?name=" + name, HttpMethod.GET, createEntity(), String.class);

                ctx = JsonPath.parse(responseEntity.getBody());

                return ctx.read("data[0].links.self");
            } catch(RuntimeException ex) {
                logger.error("The environment {} does not exists", environment, ex);
                return null;
            }

        } catch(Exception ex) {
            return null;
        }
    }

    /**
     * Read the a compose file (docker or rancher)
     * @return the compose file content
     */
    private String readComposeFile(File composeFile)  {
        if(composeFile != null && composeFile.exists() && composeFile.canWrite()) {
            try {
                return new String(Files.readAllBytes(Paths.get(composeFile.toURI())));
            } catch (IOException ex) {
                logger.error("Error while reading the compose file: {}",composeFile.getAbsolutePath(), ex);
            }
        }
        return null;
    }

    /**
     * Delete a stack
     */
    private void deleteStack() {
        String stackUrl = getStack();

        logger.info("About to delete the stack: {}", stackUrl);

        if(stackUrl != null) {
            restTemplate.exchange(stackUrl, HttpMethod.DELETE, createEntity(), String.class);

            logger.info("Stack {} successfully deleted", stackUrl);
        }
    }

    /**
     * Create a stack
     */
    private void createStack() {

        //Get the current environment
        String environmentResponse = getEnvironment();

        ReadContext ctx = JsonPath.parse(environmentResponse);

        //Get the current stack url
        String stacksUrl = ctx.read("data[0].links.stacks");

        //Read the docker compose file content
        String dockerComposeContent = readComposeFile(dockerComposeFile);
        String rancherComposeContent = readComposeFile(rancherComposeFile);

        if(dockerComposeContent != null) {

            //Construct POST request payload
            Map<String, String> payload = new HashMap<>();
            payload.put("description", description);
            payload.put("dockerCompose", dockerComposeContent);
            if(rancherComposeContent != null) {
                payload.put("rancherCompose", rancherComposeContent);
            }
            payload.put("name", name);
            payload.put("startOnCreate", startOnCreate);

            HttpHeaders headers = createBasicAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity entity = null;

            try {
                entity = new HttpEntity(new ObjectMapper().writeValueAsString(payload), headers);
            } catch (JsonProcessingException ex) {
                logger.error("Error while parsing stack payload to json", ex);
            }

            logger.info("About to create new stack with url: {}, headers: {} and payload: {}", stacksUrl,headers,payload);

            //Perform http request
            restTemplate.exchange(stacksUrl, HttpMethod.POST, entity, String.class);

            logger.info("New stack successfully created");
        }
    }

    /**
     * Rancher plugin execution
     * @throws MojoExecutionException maven plugin exception
     */
    public void execute() throws MojoExecutionException
    {
        //Delete the stack first
        deleteStack();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
            logger.error("Error while sleeping", ex);
        }

        //Create the stack
        createStack();
    }
}
