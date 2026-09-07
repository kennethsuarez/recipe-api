package com.kenenthsuarez.recipe_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Profile("!prod")
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerConfiguration {
    @Bean
    OpenAPI recipeOpenApi(@Value("${recipe.openapi.title}") String title,
                          @Value("${recipe.openapi.description}") String description,
                          @Value("${recipe.openapi.version}") String version,
                          @Value("${recipe.openapi.server-url}") String serverUrl) {
        return new OpenAPI()
                .info(new Info().title(title).description(description).version(version))
                .servers(List.of(new Server().url(serverUrl).description("Configured API server")))
                .components(new Components()
                        .addSchemas("ProblemDetail", problemSchema())
                        .addResponses("BadRequest", problemResponse(400, "Invalid request"))
                        .addResponses("NotFound", problemResponse(404, "Recipe not found"))
                        .addResponses("Conflict", problemResponse(409, "Concurrent modification conflict"))
                        .addResponses("PayloadTooLarge", problemResponse(413, "Request body exceeds the configured limit"))
                        .addResponses("ServiceUnavailable", problemResponse(503, "Service temporarily unavailable")));
    }

    private Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 Problem Details response; validation failures may also include errors")
                .addProperty("type", new StringSchema().format("uri").example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().format("int32").example(400))
                .addProperty("detail", new StringSchema().example("Request validation failed"))
                .addProperty("instance", new StringSchema().format("uri").example("/api/recipes"))
                .addProperty("errors", new ArraySchema().items(new StringSchema()
                        .example("title: must not be blank")));
    }

    private ApiResponse problemResponse(int status, String description) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ProblemDetail");
        MediaType mediaType = new MediaType().schema(schema).example(java.util.Map.of(
                "type", "about:blank",
                "title", description,
                "status", status,
                "detail", description));
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", mediaType));
    }
}
