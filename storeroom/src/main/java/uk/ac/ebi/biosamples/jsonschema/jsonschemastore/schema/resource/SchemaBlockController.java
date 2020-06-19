package uk.ac.ebi.biosamples.jsonschema.jsonschemastore.schema.resource;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.client.ValidatorClient;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.client.dto.ValidateRequestDocument;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.client.dto.ValidateResponseDocument;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.client.dto.ValidationState;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.dto.SchemaBlockDocument;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.exception.JsonSchemaServiceException;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.schema.document.SchemaBlock;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.schema.service.SchemaBlockService;
import uk.ac.ebi.biosamples.jsonschema.jsonschemastore.schema.util.JsonSchemaMappingUtil;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class SchemaBlockController {

  private final SchemaBlockService schemaBlockService;
  private final ValidatorClient validatorClient;
  private final Environment environment;

  public SchemaBlockController(
      SchemaBlockService schemaBlockService,
      ValidatorClient validatorClient,
      Environment environment) {
    this.schemaBlockService = schemaBlockService;
    this.validatorClient = validatorClient;
    this.environment = environment;
  }

  @GetMapping("/schemas")
  public ResponseEntity<List<JsonNode>> getAllSchemaBlock() {
    List<SchemaBlock> schemaBlocks = schemaBlockService.getAllSchemaBlocks();
    List<JsonNode> response =schemaBlocks.stream()
            .map(JsonSchemaMappingUtil::convertSchemaBlockToJson)
            .collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/schemas")
  public ResponseEntity<JsonNode> createSchemaBlock(@RequestBody SchemaBlockDocument schemaBlockDocument) throws JsonSchemaServiceException {
    try {
      ResponseEntity<ValidateResponseDocument> validateResult = this.validateSchema(schemaBlockDocument);
      if (HttpStatus.OK.equals(validateResult.getStatusCode()) && ValidationState.VALID.equals(Objects.requireNonNull(validateResult.getBody()).getValidationState())) {
        SchemaBlock result = schemaBlockService.createSchemaBlock(schemaBlockDocument);
        return new ResponseEntity<>(JsonSchemaMappingUtil.convertSchemaBlockToJson(result), HttpStatus.CREATED);
      } else {
        return ResponseEntity.badRequest().body(JsonSchemaMappingUtil.convertObjectToJson(validateResult.getBody()));
      }
    } catch (Exception e) {
      String errorMessage = "Error occurred while creating schema ";
      log.error(errorMessage, e);
      throw new JsonSchemaServiceException(errorMessage, e);
    }
  }

  @DeleteMapping("/schemas")
  public void deleteSchemaBlocks(@RequestBody SchemaBlockDocument schemaBlockDocument) {
    schemaBlockService.deleteSchemaBlocks(schemaBlockDocument);
  }

  @DeleteMapping("/schemas/{id}")
  public void deleteSchemaBlocksById(@PathVariable("id") String id) {
    schemaBlockService.deleteSchemaBlocksById(id);
  }

  @PutMapping("/schemas")
  public void updateSchemaBlocks(@RequestBody SchemaBlockDocument schemaBlockDocument) {
    schemaBlockService.updateSchemaBlocks(schemaBlockDocument);
  }

  private ResponseEntity<ValidateResponseDocument> validateSchema(SchemaBlockDocument schema) {
    JsonNode jsonNode = JsonSchemaMappingUtil.convertSchemaBlockToJson(schema);
    ValidateRequestDocument validateRequestDocument = new ValidateRequestDocument();
    validateRequestDocument.setObject(jsonNode);
    validateRequestDocument.setSchema(JsonSchemaMappingUtil.getSchemaObject()); // TODO: meta schema should come from document store
    return validatorClient.validate(validateRequestDocument, environment.getProperty("elixirValidator.hostUrl"));
  }
}
