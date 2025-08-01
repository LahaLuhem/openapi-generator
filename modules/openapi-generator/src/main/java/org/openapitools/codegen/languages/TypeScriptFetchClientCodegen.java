/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.DocumentationFeature;
import org.openapitools.codegen.meta.features.SecurityFeature;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.templating.mustache.IndentedLambda;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;
import static org.openapitools.codegen.utils.StringUtils.*;

public class TypeScriptFetchClientCodegen extends AbstractTypeScriptClientCodegen {
    public static final String NPM_REPOSITORY = "npmRepository";
    public static final String WITH_INTERFACES = "withInterfaces";
    public static final String USE_SINGLE_REQUEST_PARAMETER = "useSingleRequestParameter";
    public static final String PREFIX_PARAMETER_INTERFACES = "prefixParameterInterfaces";
    public static final String WITHOUT_RUNTIME_CHECKS = "withoutRuntimeChecks";
    public static final String STRING_ENUMS = "stringEnums";
    public static final String STRING_ENUMS_DESC = "Generate string enums instead of objects for enum values.";
    public static final String IMPORT_FILE_EXTENSION_SWITCH = "importFileExtension";
    public static final String IMPORT_FILE_EXTENSION_SWITCH_DESC = "File extension to use with relative imports. Set it to '.js' or '.mjs' when using [ESM](https://nodejs.org/api/esm.html).";
    public static final String FILE_NAMING = "fileNaming";
    public static final String KEBAB_CASE = "kebab-case";
    public static final String CAMEL_CASE = "camelCase";
    public static final String PASCAL_CASE = "PascalCase";
    public static final String USE_SQUARE_BRACKETS_IN_ARRAY_NAMES = "useSquareBracketsInArrayNames";
    public static final String VALIDATION_ATTRIBUTES = "validationAttributes";

    @Getter @Setter
    protected String npmRepository = null;
    @Getter @Setter
    protected String importFileExtension = "";
    private boolean useSingleRequestParameter = true;
    private boolean prefixParameterInterfaces = false;
    protected boolean addedApiIndex = false;
    protected boolean addedModelIndex = false;
    protected boolean withoutRuntimeChecks = false;
    protected boolean stringEnums = false;
    protected String fileNaming = PASCAL_CASE;

    // "Saga and Record" mode.
    public static final String SAGAS_AND_RECORDS = "sagasAndRecords";
    public static final String DETECT_PASSTHROUGH_MODELS_WITH_SUFFIX_AND_FIELD = "detectPassthroughModelsWithSuffixAndField";
    public static final String INFER_UNIQUE_ID_FROM_NAME_SUFFIX = "inferUniqueIdFromNameSuffix";
    public static final String INFER_ENTITY_FROM_UNIQUE_ID_WITH_NAME = "inferEntityFromUniqueIdWithName";
    public static final String PACKAGE_AS_SOURCE_ONLY_LIBRARY = "packageAsSourceOnlyLibrary";

    private static final String X_IS_UNIQUE_ID = "x-isUniqueId";
    private static final String X_ENTITY_ID = "x-entityId";
    private static final String X_OPERATION_RETURN_PASSTHROUGH = "x-operationReturnPassthrough";
    private static final String X_KEEP_AS_JS_OBJECT = "x-keepAsJSObject";

    protected boolean sagasAndRecords = false;
    @Getter @Setter
    protected String detectPassthroughModelsWithSuffixAndField = null; // Ex: "Response;data"
    @Setter protected boolean inferUniqueIdFromNameSuffix = false;
    @Getter @Setter
    protected String inferEntityFromUniqueIdWithName = null;
    @Setter protected boolean packageAsSourceOnlyLibrary = false;
    @Getter @Setter
    protected Boolean generateValidationAttributes = false;

    public TypeScriptFetchClientCodegen() {
        super();

        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .includeSecurityFeatures(SecurityFeature.BearerToken));

        // clear import mapping (from default generator) as TS does not use it
        // at the moment
        importMapping.clear();

        outputFolder = "generated-code/typescript-fetch";
        embeddedTemplateDir = templateDir = "typescript-fetch";

        this.apiTemplateFiles.put("apis.mustache", ".ts");

        this.addExtraReservedWords();

        supportModelPropertyNaming(CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.camelCase);
        this.cliOptions.add(new CliOption(NPM_REPOSITORY, "Use this property to set an url your private npmRepo in the package.json"));
        this.cliOptions.add(new CliOption(WITH_INTERFACES, "Setting this property to true will generate interfaces next to the default class implementations.", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER, CodegenConstants.USE_SINGLE_REQUEST_PARAMETER_DESC, SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.TRUE.toString()));
        this.cliOptions.add(new CliOption(PREFIX_PARAMETER_INTERFACES, "Setting this property to true will generate parameter interface declarations prefixed with API class name to avoid name conflicts.", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(WITHOUT_RUNTIME_CHECKS, "Setting this property to true will remove any runtime checks on the request and response payloads. Payloads will be casted to their expected types.", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(SAGAS_AND_RECORDS, "Setting this property to true will generate additional files for use with redux-saga and immutablejs.", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(STRING_ENUMS, STRING_ENUMS_DESC, SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(IMPORT_FILE_EXTENSION_SWITCH, IMPORT_FILE_EXTENSION_SWITCH_DESC).defaultValue(""));
        this.cliOptions.add(new CliOption(FILE_NAMING, "Naming convention for the output files: 'PascalCase', 'camelCase', 'kebab-case'.").defaultValue(this.fileNaming));
        this.cliOptions.add(new CliOption(USE_SQUARE_BRACKETS_IN_ARRAY_NAMES, "Setting this property to true will add brackets to array attribute names, e.g. my_values[].", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(VALIDATION_ATTRIBUTES, "Setting this property to true will generate the validation attributes of model properties.", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
    }

    @Override
    public String toApiFilename(String name) {
        return convertUsingFileNamingConvention(super.toApiFilename(name));
    }

    @Override
    public String toModelFilename(String name) {
        return convertUsingFileNamingConvention(super.toModelFilename(name));
    }

    /**
     * Converts the original name according to the current <code>fileNaming</code> strategy.
     *
     * @param originalName the original name to transform
     * @return the transformed name
     */
    private String convertUsingFileNamingConvention(String originalName) {
        String name = originalName;
        if (KEBAB_CASE.equals(fileNaming)) {
            name = dashize(underscore(name));
        } else if (CAMEL_CASE.equals(fileNaming)) {
            name = camelize(name, LOWERCASE_FIRST_LETTER);
        }
        return name;
    }

    @Override
    public String getName() {
        return "typescript-fetch";
    }

    @Override
    public String getHelp() {
        return "Generates a TypeScript client library using Fetch API (beta).";
    }

    public Boolean getWithoutRuntimeChecks() {
        return withoutRuntimeChecks;
    }

    public void setWithoutRuntimeChecks(Boolean withoutRuntimeChecks) {
        this.withoutRuntimeChecks = withoutRuntimeChecks;
    }

    public Boolean getStringEnums() {
        return this.stringEnums;
    }

    public void setStringEnums(Boolean stringEnums) {
        this.stringEnums = stringEnums;
    }

    /**
     * Set the file naming type.
     *
     * @param fileNaming the file naming to use
     */
    public void setFileNaming(String fileNaming) {
        if (PASCAL_CASE.equals(fileNaming) || CAMEL_CASE.equals(fileNaming) || KEBAB_CASE.equals(fileNaming)) {
            this.fileNaming = fileNaming;
        } else {
            throw new IllegalArgumentException("Invalid file naming '" +
                    fileNaming + "'. Must be 'PascalCase', 'camelCase' or 'kebab-case'");
        }
    }

    public Boolean getSagasAndRecords() {
        return sagasAndRecords;
    }

    public void setSagasAndRecords(Boolean sagasAndRecords) {
        this.sagasAndRecords = sagasAndRecords;
    }

    public String getPassthroughSuffix() {
        return detectPassthroughModelsWithSuffixAndField != null ? detectPassthroughModelsWithSuffixAndField.split("\\.")[0] : null;
    }

    public String getPassthroughField() {
        return detectPassthroughModelsWithSuffixAndField != null ? detectPassthroughModelsWithSuffixAndField.split("\\.")[1] : null;
    }

    public boolean getInferUniqueIdFromNameSuffix() {
        return inferUniqueIdFromNameSuffix;
    }

    public boolean getPackageAsSourceOnlyLibrary() {
        return packageAsSourceOnlyLibrary;
    }

    public boolean isUniqueIdAccordingToNameSuffix(String name) {
        if (name == null) {
            return false;
        }
        return "id".equals(name) ||
                "ids".equals(name) ||
                (name.length() >= 3 && name.endsWith("Id")) ||
                (name.length() >= 4 && name.endsWith("Ids"));
    }

    @Override
    public void processOpts() {
        super.processOpts();
        additionalProperties.put("isOriginalModelPropertyNaming", getModelPropertyNaming() == CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.original);
        additionalProperties.put("modelPropertyNaming", getModelPropertyNaming().name());

        String sourceDir = "";
        if (additionalProperties.containsKey(NPM_NAME)) {
            sourceDir = "src" + File.separator;
        }

        this.apiPackage = sourceDir + "apis";
        this.modelPackage = sourceDir + "models";

        supportingFiles.add(new SupportingFile("index.mustache", sourceDir, "index.ts"));
        supportingFiles.add(new SupportingFile("runtime.mustache", sourceDir, "runtime.ts"));

        if (additionalProperties.containsKey(IMPORT_FILE_EXTENSION_SWITCH)) {
            this.setImportFileExtension(additionalProperties.get(IMPORT_FILE_EXTENSION_SWITCH).toString());
        }
        writePropertyBack(IMPORT_FILE_EXTENSION_SWITCH, getImportFileExtension());

        if (additionalProperties.containsKey(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER)) {
            this.setUseSingleRequestParameter(convertPropertyToBoolean(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER));
        }
        writePropertyBack(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER, getUseSingleRequestParameter());

        if (additionalProperties.containsKey(PREFIX_PARAMETER_INTERFACES)) {
            this.setPrefixParameterInterfaces(convertPropertyToBoolean(PREFIX_PARAMETER_INTERFACES));
        }
        writePropertyBack(PREFIX_PARAMETER_INTERFACES, getPrefixParameterInterfaces());

        if (additionalProperties.containsKey(NPM_NAME)) {
            addNpmPackageGeneration();
        }

        if (additionalProperties.containsKey(WITHOUT_RUNTIME_CHECKS)) {
            this.setWithoutRuntimeChecks(convertPropertyToBoolean(WITHOUT_RUNTIME_CHECKS));
        }

        if (additionalProperties.containsKey(STRING_ENUMS)) {
            this.setStringEnums(convertPropertyToBoolean(STRING_ENUMS));
        }

        if (additionalProperties.containsKey(FILE_NAMING)) {
            this.setFileNaming(additionalProperties.get(FILE_NAMING).toString());
        }

        if (!withoutRuntimeChecks) {
            this.modelTemplateFiles.put("models.mustache", ".ts");
            typeMapping.put("date", "Date");
            typeMapping.put("DateTime", "Date");
        }

        if (additionalProperties.containsKey(SAGAS_AND_RECORDS)) {
            this.setSagasAndRecords(convertPropertyToBoolean(SAGAS_AND_RECORDS));
            if (this.getSagasAndRecords()) {
                apiTemplateFiles.put("sagas.mustache", "Sagas.ts");
                modelTemplateFiles.put("records.mustache", "Record.ts");
                supportingFiles.add(new SupportingFile("runtimeSagasAndRecords.mustache", sourceDir, "runtimeSagasAndRecords.ts"));
                supportingFiles.add(new SupportingFile("ApiEntitiesRecord.mustache", sourceDir, "ApiEntitiesRecord.ts"));
                supportingFiles.add(new SupportingFile("ApiEntitiesReducer.mustache", sourceDir, "ApiEntitiesReducer.ts"));
                supportingFiles.add(new SupportingFile("ApiEntitiesSelectors.mustache", sourceDir, "ApiEntitiesSelectors.ts"));

                if (additionalProperties.containsKey(DETECT_PASSTHROUGH_MODELS_WITH_SUFFIX_AND_FIELD)) {
                    this.setDetectPassthroughModelsWithSuffixAndField((String) additionalProperties.get(DETECT_PASSTHROUGH_MODELS_WITH_SUFFIX_AND_FIELD));
                }
                if (additionalProperties.containsKey(INFER_UNIQUE_ID_FROM_NAME_SUFFIX)) {
                    this.setInferUniqueIdFromNameSuffix(convertPropertyToBoolean(INFER_UNIQUE_ID_FROM_NAME_SUFFIX));
                }
                if (additionalProperties.containsKey(INFER_ENTITY_FROM_UNIQUE_ID_WITH_NAME)) {
                    this.setInferEntityFromUniqueIdWithName((String) additionalProperties.get(INFER_ENTITY_FROM_UNIQUE_ID_WITH_NAME));
                }
                if (additionalProperties.containsKey(PACKAGE_AS_SOURCE_ONLY_LIBRARY)) {
                    this.setPackageAsSourceOnlyLibrary(convertPropertyToBoolean(PACKAGE_AS_SOURCE_ONLY_LIBRARY));
                }

                this.addExtraReservedWordsForSagasAndRecords();

                if (this.getPackageAsSourceOnlyLibrary()) {
                    supportingFiles.add(new SupportingFile("sourceLibraryIndex.mustache", "", "index.ts"));
                }
            }
        }

        setGenerateValidationAttributes(convertPropertyToBooleanAndWriteBack(VALIDATION_ATTRIBUTES));
    }

    @Override
    public String toEnumDefaultValue(String value, String datatype) {
        if (this.getSagasAndRecords()) {
            return datatype + "." + value;
        }
        return super.toEnumDefaultValue(value, datatype);
    }

    @Override
    protected String getEnumDefaultValue(String defaultValue, String dataType) {
        if (this.getSagasAndRecords()) {
            return defaultValue;
        }
        return super.getEnumDefaultValue(defaultValue, dataType);
    }


    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        ImmutableMap.Builder<String, Mustache.Lambda> lambdas = super.addMustacheLambdas();
        lambdas.put("indented_star_1", new IndentedLambda(1, " ", "* ", false, false));
        lambdas.put("indented_star_4", new IndentedLambda(5, " ", "* ", false, false));
        return lambdas;
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isFileSchema(p)) {
            return "Blob";
        } else if (ModelUtils.isBinarySchema(p)) {
            return "Blob";
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
        codegenModel.additionalPropertiesType = getTypeDeclaration(ModelUtils.getAdditionalProperties(schema));
        addImport(codegenModel, codegenModel.additionalPropertiesType);
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        List<ModelMap> models = postProcessModelsEnum(objs).getModels();

        // process enum and custom properties in models
        for (ModelMap mo : models) {
            ExtendedCodegenModel cm = (ExtendedCodegenModel) mo.getModel();
            cm.imports = new TreeSet<>(cm.imports);
            this.processCodeGenModel(cm);
        }

        // Add supporting file only if we plan to generate files in /models
        if (!objs.isEmpty() && !addedModelIndex) {
            addedModelIndex = true;
            supportingFiles.add(new SupportingFile("models.index.mustache", modelPackage().replace('.', File.separatorChar), "index.ts"));
        }

        return objs;
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);
        if (parameter.isFormParam && parameter.isArray && "binary".equals(parameter.dataFormat)) {
            parameter.isCollectionFormatMulti = true;
        }
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        List<ExtendedCodegenModel> allModels = new ArrayList<>();
        List<String> entityModelClassnames = new ArrayList<>();

        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap entry : result.values()) {
            for (ModelMap model : entry.getModels()) {
                ExtendedCodegenModel codegenModel = (ExtendedCodegenModel) model.getModel();
                boolean importsPresent = !codegenModel.imports.isEmpty();

                // When legacyDiscriminatorBehaviour = false, DefaultCodegen will add the mapped models of the
                // discriminator to codegenModel.imports, causing us to duplicate the import if we don't remove them
                CodegenDiscriminator discriminator = codegenModel.discriminator;
                boolean mappedDiscriminatorModelsPresent = nonNull(discriminator)
                        && nonNull(discriminator.getMappedModels());
                if (importsPresent && mappedDiscriminatorModelsPresent) {
                    Set<String> mappedDiscriminatorModelNames = discriminator.getMappedModels()
                            .stream()
                            .map(CodegenDiscriminator.MappedModel::getModelName)
                            .collect(Collectors.toSet());
                    Set<String> filteredImports = codegenModel.imports
                            .stream()
                            .filter(modelImport ->
                                    !mappedDiscriminatorModelNames.contains(modelImport))
                            .collect(Collectors.toSet());

                    codegenModel.imports.clear();
                    codegenModel.imports.addAll(filteredImports);
                }

                model.put("hasImports", importsPresent);
                model.put("tsImports", toTsImports(codegenModel, parseImports(codegenModel)));


                allModels.add(codegenModel);
                if (codegenModel.isEntity) {
                    entityModelClassnames.add(codegenModel.classname);
                }
            }
        }

        for (ExtendedCodegenModel rootModel : allModels) {
            for (String curImport : rootModel.imports) {
                boolean isModelImport = false;
                for (ExtendedCodegenModel model : allModels) {
                    if (model.classname.equals(curImport) && !model.isEnum) {
                        isModelImport = true;
                        break;
                    }
                }
                if (isModelImport) {
                    rootModel.modelImports.add(curImport);
                }
            }

            for (CodegenProperty cpVar : rootModel.vars) {
                ExtendedCodegenProperty var = (ExtendedCodegenProperty) cpVar;
                if (var.isModel && entityModelClassnames.indexOf(var.dataType) != -1) {
                    var.isEntity = true;
                } else if (var.isArray && var.items.isModel && entityModelClassnames.indexOf(var.items.dataType) != -1) {
                    ((ExtendedCodegenProperty) var.items).isEntity = true;
                }
            }
        }
        return result;
    }

    /**
     * Parse imports
     */
    private Set<String> parseImports(CodegenModel cm) {
        Set<String> newImports = new HashSet<>();
        if (cm.imports.size() > 0) {
            for (String name : cm.imports) {
                if (name.indexOf(" | ") >= 0) {
                    String[] parts = name.split(" \\| ");
                    Collections.addAll(newImports, parts);
                } else {
                    newImports.add(name);
                }
            }
        }
        return newImports;
    }

    private List<Map<String, String>> toTsImports(CodegenModel cm, Set<String> imports) {
        List<Map<String, String>> tsImports = new ArrayList<>();
        for (String im : imports) {
            if (!im.equals(cm.classname)) {
                HashMap<String, String> tsImport = new HashMap<>();
                // TVG: This is used as class name in the import statements of the model file
                tsImport.put("classname", im);
                tsImport.put("filename", convertUsingFileNamingConvention(im));
                tsImports.add(tsImport);
            }
        }
        return tsImports;
    }

    private void autoSetDefaultValueForProperty(ExtendedCodegenProperty var) {
        if (var.isArray || var.isModel) {
            var.defaultValue = var.dataTypeAlternate + "()";
        } else if (var.isUniqueId) {
            var.defaultValue = "\"-1\"";
        } else if (var.isEnum) {
            var.defaultValue = "'" + var._enum.get(0) + "'";
            updateCodegenPropertyEnum(var);
        } else if (var.dataType.equalsIgnoreCase("string")) {
            var.defaultValue = "\"\"";
        } else if (var.dataType.equalsIgnoreCase("number")) {
            var.defaultValue = "0";
        } else if (var.dataType.equalsIgnoreCase("boolean")) {
            var.defaultValue = "false";
        } else {
            if (var.allowableValues != null && var.allowableValues.get("enumVars") instanceof ArrayList && ((ArrayList) var.allowableValues.get("enumVars")).get(0) instanceof HashMap) {
                var.defaultValue = var.dataTypeAlternate + "." + ((HashMap<String, String>) ((ArrayList) var.allowableValues.get("enumVars")).get(0)).get("name");
            }
        }
    }

    private void addNpmPackageGeneration() {

        if (additionalProperties.containsKey(NPM_REPOSITORY)) {
            this.setNpmRepository(additionalProperties.get(NPM_REPOSITORY).toString());
        }

        //Files for building our lib
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("package.mustache", "", "package.json"));
        supportingFiles.add(new SupportingFile("tsconfig.mustache", "", "tsconfig.json"));
        // in case ECMAScript 6 is supported add another tsconfig for an ESM (ECMAScript Module)
        if (supportsES6) {
            supportingFiles.add(new SupportingFile("tsconfig.esm.mustache", "", "tsconfig.esm.json"));
        }
        supportingFiles.add(new SupportingFile("npmignore.mustache", "", ".npmignore"));
        supportingFiles.add(new SupportingFile("gitignore", "", ".gitignore"));
    }

    @Override
    public List<CodegenParameter> fromRequestBodyToFormParameters(RequestBody body, Set<String> imports) {
        List<CodegenParameter> superParams = super.fromRequestBodyToFormParameters(body, imports);
        List<CodegenParameter> extendedParams = new ArrayList<CodegenParameter>();
        for (CodegenParameter cp : superParams) {
            extendedParams.add(new ExtendedCodegenParameter(cp));
        }
        return extendedParams;
    }

    @Override
    public ExtendedCodegenParameter fromParameter(Parameter parameter, Set<String> imports) {
        CodegenParameter cp = super.fromParameter(parameter, imports);
        return new ExtendedCodegenParameter(cp);
    }

    @Override
    public CodegenParameter fromFormProperty(String name, Schema propertySchema, Set<String> imports) {
        CodegenParameter cp = super.fromFormProperty(name, propertySchema, imports);
        return new ExtendedCodegenParameter(cp);
    }

    @Override
    public CodegenParameter fromRequestBody(RequestBody body, Set<String> imports, String bodyParameterName) {
        CodegenParameter cp = super.fromRequestBody(body, imports, bodyParameterName);
        return new ExtendedCodegenParameter(cp);
    }

    @Override
    public ExtendedCodegenProperty fromProperty(String name, Schema p, boolean required) {
        CodegenProperty cp = super.fromProperty(name, p, required);
        return new ExtendedCodegenProperty(cp);
    }

    @Override
    public ExtendedCodegenModel fromModel(String name, Schema model) {
        CodegenModel cm = super.fromModel(name, model);
        return new ExtendedCodegenModel(cm);
    }

    @Override
    public ExtendedCodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        CodegenOperation superOp = super.fromOperation(path, httpMethod, operation, servers);
        ExtendedCodegenOperation op = new ExtendedCodegenOperation(superOp);

        if (this.getSagasAndRecords()) {
            ApiResponse methodResponse = findMethodResponse(operation.getResponses());
            if (methodResponse != null) {
                Map<String, Schema> schemas = ModelUtils.getSchemas(this.openAPI);
                Schema schema = null;
                if (schemas != null) {
                    schema = schemas.get(op.returnBaseType);
                }

                ExtendedCodegenModel cm = null;
                if (schema != null) {
                    cm = fromModel(op.returnBaseType, schema);

                    Object returnPassthrough = cm.vendorExtensions.get(X_OPERATION_RETURN_PASSTHROUGH);
                    if (returnPassthrough instanceof String) {
                        if (((String) returnPassthrough).isEmpty()) {
                            op.hasReturnPassthroughVoid = true;
                            op.returnPassthrough = null;
                        } else {
                            boolean foundMatch = false;
                            for (CodegenProperty var : cm.vars) {
                                if (var.name.equals(returnPassthrough)) {
                                    foundMatch = true;
                                    break;
                                }
                            }
                            if (foundMatch) {
                                op.returnPassthrough = (String) returnPassthrough;
                            } else { // no match, treat as if empty.
                                op.hasReturnPassthroughVoid = true;
                                op.returnPassthrough = null;
                            }
                        }
                    } else if (this.getDetectPassthroughModelsWithSuffixAndField() != null && op.returnBaseType.length() > this.getPassthroughSuffix().length() && op.returnBaseType.endsWith(this.getPassthroughSuffix())) {
                        boolean foundMatch = false;
                        for (CodegenProperty var : cm.vars) {
                            if (var.name.equals(this.getPassthroughField())) {
                                foundMatch = true;
                                break;
                            }
                        }
                        if (foundMatch) {
                            op.returnPassthrough = this.getPassthroughField();
                        } else { // no match, treat as if empty.
                            op.hasReturnPassthroughVoid = true;
                            op.returnPassthrough = null;
                        }
                    }
                }

                if (!op.hasReturnPassthroughVoid) {
                    Schema responseSchema = unaliasSchema(ModelUtils.getSchemaFromResponse(openAPI, methodResponse));
                    ExtendedCodegenProperty cp = null;
                    if (op.returnPassthrough instanceof String && cm != null) {
                        cp = (ExtendedCodegenProperty) this.processCodeGenModel(cm).vars.get(1);
                    } else if (responseSchema != null) {
                        cp = fromProperty("response", responseSchema, false);
                        this.processCodegenProperty(cp, "", null);
                    }

                    op.returnBaseTypeAlternate = null;
                    if (cp != null) {
                        op.returnTypeAlternate = cp.dataTypeAlternate;
                        op.returnTypeIsModel = cp.isModel;
                        op.returnTypeIsArray = cp.isArray;
                        if (cp.isArray) {
                            if (cp.items.isModel) {
                                op.returnTypeSupportsEntities = true;
                                op.returnBaseTypeAlternate = cp.items.dataType + "Record";
                            } else if (cp.items.allowableValues != null) {
                                op.returnBaseTypeAlternate = cp.items.dataType;
                            }
                        } else if (cp.isModel) {
                            op.returnTypeSupportsEntities = true;
                            op.returnBaseTypeAlternate = cp.dataTypeAlternate;
                        } else if (cp.allowableValues != null) {
                            op.returnBaseTypeAlternate = cp.dataTypeAlternate;
                        }

                    }
                }
            }
        }

        return op;
    }

    @Override
    public String escapeReservedWord(String name) {
        if (this.getSagasAndRecords()) {
            if (this.reservedWordsMappings().containsKey(name)) {
                return this.reservedWordsMappings().get(name);
            }
            return "_" + name;
        } else {
            return super.escapeReservedWord(name);
        }
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap operations, List<ModelMap> allModels) {
        // Add supporting file only if we plan to generate files in /apis
        if (!operations.isEmpty() && !addedApiIndex) {
            addedApiIndex = true;
            supportingFiles.add(new SupportingFile("apis.index.mustache", apiPackage().replace('.', File.separatorChar), "index.ts"));
            if (this.getSagasAndRecords()) {
                supportingFiles.add(new SupportingFile("sagaApiManager.mustache", apiPackage().replace('.', File.separatorChar), "SagaApiManager.ts"));
                supportingFiles.add(new SupportingFile("allSagas.mustache", apiPackage().replace('.', File.separatorChar), "allSagas.ts"));
            }
        }

        // Add supporting file only if we plan to generate files in /models
        if (!allModels.isEmpty() && !addedModelIndex) {
            addedModelIndex = true;
            supportingFiles.add(new SupportingFile("models.index.mustache", modelPackage().replace('.', File.separatorChar), "index.ts"));
        }

        this.addOperationModelImportInformation(operations);
        this.escapeOperationIds(operations);
        this.updateOperationParameterForEnum(operations);
        if (this.getSagasAndRecords()) {
            this.updateOperationParameterForSagaAndRecords(operations);
        }
        this.addOperationObjectResponseInformation(operations);
        this.addOperationPrefixParameterInterfacesInformation(operations);

        return operations;
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Map<String, Object> parentObjs = super.postProcessSupportingFileData(objs);

        parentObjs.put("useSagaAndRecords", this.getSagasAndRecords());

        return parentObjs;
    }

    private ExtendedCodegenModel processCodeGenModel(ExtendedCodegenModel cm) {
        if (this.getSagasAndRecords()) {
            Object xEntityId = cm.vendorExtensions.get(X_ENTITY_ID);
            if (xEntityId == null && this.getInferEntityFromUniqueIdWithName() != null) {
                xEntityId = this.getInferEntityFromUniqueIdWithName();
            }
            Object vendorKeepAsJSObject = cm.vendorExtensions.get(X_KEEP_AS_JS_OBJECT);
            String[] propertiesToKeepAsJSObject = null;
            if (vendorKeepAsJSObject instanceof String) {
                propertiesToKeepAsJSObject = ((String) vendorKeepAsJSObject).split(",");
            }

            for (CodegenProperty cpVar : cm.vars) {
                ExtendedCodegenProperty var = (ExtendedCodegenProperty) cpVar;
                if (propertiesToKeepAsJSObject != null && Arrays.asList(propertiesToKeepAsJSObject).contains(var.name)) {
                    var.keepAsJSObject = true;
                }
                boolean parentIsEntity = this.processCodegenProperty(var, cm.classname, xEntityId);
                if (parentIsEntity) {
                    cm.isEntity = true;
                }
            }

            Object returnPassthrough = cm.vendorExtensions.get(X_OPERATION_RETURN_PASSTHROUGH);
            if (returnPassthrough instanceof String) {
                if (((String) returnPassthrough).isEmpty()) {
                    cm.hasReturnPassthroughVoid = true;
                    cm.returnPassthrough = null;
                } else {
                    boolean foundMatch = false;
                    for (CodegenProperty var : cm.vars) {
                        if (var.name.equals(returnPassthrough)) {
                            foundMatch = true;
                            break;
                        }
                    }
                    if (foundMatch) {
                        cm.returnPassthrough = (String) returnPassthrough;
                    } else { // no match, treat as if empty.
                        cm.hasReturnPassthroughVoid = true;
                        cm.returnPassthrough = null;
                    }
                }
            } else if (this.getDetectPassthroughModelsWithSuffixAndField() != null && cm.name.length() > this.getPassthroughSuffix().length() && cm.name.endsWith(this.getPassthroughSuffix())) {
                boolean foundMatch = false;
                for (CodegenProperty var : cm.vars) {
                    if (var.name.equals(this.getPassthroughField())) {
                        foundMatch = true;
                        break;
                    }
                }
                if (foundMatch) {
                    cm.returnPassthrough = this.getPassthroughField();
                } else { // no match, treat as if empty.
                    cm.hasReturnPassthroughVoid = true;
                    cm.returnPassthrough = null;
                }
            }
        } else {
            for (CodegenProperty cpVar : cm.vars) {
                ExtendedCodegenProperty var = (ExtendedCodegenProperty) cpVar;
                this.processCodegenProperty(var, cm.classname, null);
            }
        }

        if (cm.parent != null) {
            for (CodegenProperty cpVar : cm.allVars) {
                ExtendedCodegenProperty var = (ExtendedCodegenProperty) cpVar;

                if (Boolean.TRUE.equals(var.isEnum)) {
                    var.datatypeWithEnum = var.datatypeWithEnum
                            .replace(var.enumName, cm.classname + var.enumName);
                }
            }
        }

        List<CodegenProperty> oneOfsList = Optional.ofNullable(cm.getComposedSchemas())
                .map(CodegenComposedSchemas::getOneOf)
                .orElse(Collections.emptyList());

        // create a set of any non-primitive, non-array types used in the oneOf schemas which will
        // need to be imported.
        cm.oneOfModels = oneOfsList.stream()
                .filter(cp -> !cp.getIsPrimitiveType() && !cp.getIsArray())
                .map(CodegenProperty::getBaseType)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        // create a set of any complex, inner types used by arrays in the oneOf schema (e.g. if
        // the oneOf uses Array<Foo>, Foo needs to be imported).
        cm.oneOfArrays = oneOfsList.stream()
                .filter(CodegenProperty::getIsArray)
                .map(CodegenProperty::getComplexType)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        // create a set of primitive types used in the oneOf schemas for use in the to & from
        // typed JSON methods.
        cm.oneOfPrimitives = oneOfsList.stream()
                .filter(CodegenProperty::getIsPrimitiveType)
                .collect(Collectors.toCollection(HashSet::new));

        if (!cm.oneOf.isEmpty()) {
            // For oneOfs only import $refs within the oneOf
            cm.imports = cm.imports.stream()
                    .filter(im -> cm.oneOfModels.contains(im) || cm.oneOfArrays.contains(im))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        return cm;
    }

    private boolean processCodegenProperty(ExtendedCodegenProperty var, String parentClassName, Object xEntityId) {
        // name enum with model name, e.g. StatusEnum => PetStatusEnum
        if (Boolean.TRUE.equals(var.isEnum)) {
            // behaviour for enum names is specific for Typescript Fetch, not using namespaces
            var.datatypeWithEnum = var.datatypeWithEnum.replace(var.enumName, parentClassName + var.enumName);

            // need to post-process defaultValue, was computed with previous var.datatypeWithEnum
            if (var.defaultValue != null && !var.defaultValue.equals("undefined")) {
                int dotPos = var.defaultValue.indexOf(".");
                if (dotPos != -1) {
                    var.defaultValue = var.datatypeWithEnum + var.defaultValue.substring(dotPos);
                }
            }
        }

        boolean parentIsEntity = false;
        if (this.getSagasAndRecords()) {
            if (var.vendorExtensions.get(X_IS_UNIQUE_ID) instanceof Boolean) {
                var.isUniqueId = Boolean.TRUE.equals(var.vendorExtensions.get(X_IS_UNIQUE_ID));
            } else if (this.getInferUniqueIdFromNameSuffix() && (var.isArray && "number".equals(var.items.dataType)) || ("number".equals(var.dataType))) {
                var.isUniqueId = this.isUniqueIdAccordingToNameSuffix(var.name);
            }
            if (var.isUniqueId && xEntityId != null && xEntityId.equals(var.name)) {
                parentIsEntity = true;
            }

            var.dataTypeAlternate = var.dataType;
            if (var.isArray) {
                var.isUniqueId = var.isUniqueId || var.itemsAreUniqueId();
                var.dataTypeAlternate = var.dataType.replace("Array<", "List<");
                String newItemsDataType = var.getItemsDataType();
                if (var.items.isModel) {
                    newItemsDataType = var.items.dataType + "Record";
                    var.dataTypeAlternate = var.dataTypeAlternate.replace(var.items.dataType, newItemsDataType);
                } else if (var.items.isEnum) {
                    newItemsDataType = var.items.datatypeWithEnum;
                    var.dataTypeAlternate = var.dataTypeAlternate.replace(var.items.dataType, newItemsDataType);
                } else if (var.isUniqueId) {
                    newItemsDataType = "string";
                    var.dataTypeAlternate = var.dataTypeAlternate.replace("number", newItemsDataType);
                }
            } else if (var.isEnum) {
                var.dataTypeAlternate = var.datatypeWithEnum;
            } else if (var.isModel) {
                var.dataTypeAlternate = var.dataType + "Record";
            } else if (var.isUniqueId) {
                var.dataTypeAlternate = "string";
                if (var.isNullable) {
                    var.dataTypeAlternate = var.dataTypeAlternate + " | null";
                }
            }
            if (var.defaultValue == null || var.defaultValue.equals("undefined")) {
                this.autoSetDefaultValueForProperty(var);
            }
        }
        return parentIsEntity;
    }

    private void escapeOperationIds(OperationsMap operations) {
        for (CodegenOperation _op : operations.getOperations().getOperation()) {
            ExtendedCodegenOperation op = (ExtendedCodegenOperation) _op;
            String param = op.operationIdCamelCase + "Request";
            if (op.imports.contains(param)) {
                // we import a model with the same name as the generated operation, escape it
                op.operationIdCamelCase += "Operation";
                op.operationIdLowerCase += "operation";
                op.operationIdSnakeCase += "_operation";
            }
        }
    }

    private void addOperationModelImportInformation(OperationsMap operations) {
        // This method will add extra information to the operations.imports array.
        // The api template uses this information to import all the required
        // models for a given operation.
        List<Map<String, String>> imports = operations.getImports();
        List<String> existingRecordClassNames = new ArrayList<>();
        List<String> existingClassNames = new ArrayList<>();
        for (Map<String, String> im : imports) {
            String className = im.get("import").replace(modelPackage() + ".", "");
            existingClassNames.add(className);
            existingRecordClassNames.add(className + "Record");
            im.put("className", className);
        }

        if (this.getSagasAndRecords()) {
            Set<String> additionalPassthroughImports = new TreeSet<>();
            for (CodegenOperation _op : operations.getOperations().getOperation()) {
                ExtendedCodegenOperation op = (ExtendedCodegenOperation) _op;
                if (op.returnPassthrough != null && op.returnBaseTypeAlternate != null) {
                    if (op.returnTypeSupportsEntities && !existingRecordClassNames.contains(op.returnBaseTypeAlternate)) {
                        additionalPassthroughImports.add(op.returnBaseTypeAlternate);
                    } else if (!op.returnTypeSupportsEntities && !existingClassNames.contains(op.returnBaseTypeAlternate)) {
                        additionalPassthroughImports.add(op.returnBaseTypeAlternate);
                    }
                }
            }
            operations.put("passthroughImports", additionalPassthroughImports);
            operations.put("hasPassthroughImports", additionalPassthroughImports.size() > 0);
        }
    }

    private void updateOperationParameterForEnum(OperationsMap operations) {
        // This method will add extra information as to whether or not we have enums and
        // update their names with the operation.id prefixed.
        // It will also set the uniqueId status if provided.
        boolean hasEnum = false;
        for (CodegenOperation _op : operations.getOperations().getOperation()) {
            ExtendedCodegenOperation op = (ExtendedCodegenOperation) _op;
            for (CodegenParameter cpParam : op.allParams) {
                ExtendedCodegenParameter param = (ExtendedCodegenParameter) cpParam;

                if (Boolean.TRUE.equals(param.isEnum)) {
                    hasEnum = true;
                    param.datatypeWithEnum = param.datatypeWithEnum
                            .replace(param.enumName, op.operationIdCamelCase + param.enumName);
                }
            }
        }

        operations.put("hasEnums", hasEnum);
    }

    private void updateOperationParameterForSagaAndRecords(OperationsMap operations) {
        // This method will add extra information as to whether or not we have enums and
        // update their names with the operation.id prefixed.
        // It will also set the uniqueId status if provided.
        for (CodegenOperation _op : operations.getOperations().getOperation()) {
            ExtendedCodegenOperation op = (ExtendedCodegenOperation) _op;
            for (CodegenParameter cpParam : op.allParams) {
                ExtendedCodegenParameter param = (ExtendedCodegenParameter) cpParam;

                if (param.vendorExtensions.get(X_IS_UNIQUE_ID) instanceof Boolean) {
                    param.isUniqueId = Boolean.TRUE.equals(param.vendorExtensions.get(X_IS_UNIQUE_ID));
                } else if (this.getInferUniqueIdFromNameSuffix() && (param.isArray && "number".equals(param.items.dataType)) || ("number".equals(param.dataType))) {
                    param.isUniqueId = this.isUniqueIdAccordingToNameSuffix(param.paramName);
                }


                param.dataTypeAlternate = param.dataType;
                if (param.isArray) {
                    param.isUniqueId = param.isUniqueId || param.itemsAreUniqueId();
                    param.dataTypeAlternate = param.dataType.replace("Array<", "List<");
                    String newItemsDataType = param.getItemsDataType();
                    if (param.items.isModel) {
                        newItemsDataType = param.items.dataType + "Record";
                        param.dataTypeAlternate = param.dataTypeAlternate.replace(param.items.dataType, newItemsDataType);
                    } else if (param.items.isEnum) {
                        newItemsDataType = param.datatypeWithEnum.substring(param.datatypeWithEnum.lastIndexOf("<") + 1, param.datatypeWithEnum.indexOf(">"));
                        param.dataTypeAlternate = param.datatypeWithEnum.replace("Array<", "List<");
                    } else if (param.isUniqueId) {
                        newItemsDataType = "string";
                        param.dataTypeAlternate = param.dataTypeAlternate.replace("number", newItemsDataType);
                    }
                } else if (param.isEnum) {
                    param.dataTypeAlternate = param.datatypeWithEnum;
                } else if (param.isModel) {
                    param.dataTypeAlternate = param.dataType + "Record";
                } else if (param.isUniqueId) {
                    param.dataTypeAlternate = "string";
                    if (param.isNullable) {
                        param.dataTypeAlternate = param.dataTypeAlternate + " | null";
                    }
                }
            }
        }
    }

    private void addOperationObjectResponseInformation(OperationsMap operations) {
        // This method will modify the information on the operations' return type.
        // The api template uses this information to know when to return a text
        // response for a given simple response operation.
        for (CodegenOperation _op : operations.getOperations().getOperation()) {
            ExtendedCodegenOperation op = (ExtendedCodegenOperation) _op;
            if ("object".equals(op.returnType)) {
                op.isMap = true;
                op.returnSimpleType = false;
            }
        }
    }

    private void addOperationPrefixParameterInterfacesInformation(Map<String, Object> operations) {
        operations.put("prefixParameterInterfaces", getPrefixParameterInterfaces());
    }

    private void addExtraReservedWords() {
        this.reservedWords.add("BASE_PATH");
        this.reservedWords.add("BaseAPI");
        this.reservedWords.add("RequiredError");
        this.reservedWords.add("COLLECTION_FORMATS");
        this.reservedWords.add("FetchAPI");
        this.reservedWords.add("ConfigurationParameters");
        this.reservedWords.add("Configuration");
        this.reservedWords.add("configuration");
        this.reservedWords.add("HTTPMethod");
        this.reservedWords.add("HTTPHeaders");
        this.reservedWords.add("HTTPQuery");
        this.reservedWords.add("HTTPBody");
        this.reservedWords.add("ModelPropertyNaming");
        this.reservedWords.add("FetchParams");
        this.reservedWords.add("RequestOpts");
        this.reservedWords.add("exists");
        this.reservedWords.add("RequestContext");
        this.reservedWords.add("ResponseContext");
        this.reservedWords.add("Middleware");
        this.reservedWords.add("ApiResponse");
        this.reservedWords.add("ResponseTransformer");
        this.reservedWords.add("JSONApiResponse");
        this.reservedWords.add("VoidApiResponse");
        this.reservedWords.add("BlobApiResponse");
        this.reservedWords.add("TextApiResponse");
        // "Index" would create a file "Index.ts" which on case insensitive filesystems
        // would override our "index.js" file
        this.reservedWords.add("Index");
    }

    private void addExtraReservedWordsForSagasAndRecords() {
        // immutablejs Records have potentially many reserved words. Adding only strict minimum for now.
        this.reservedWords.add("entries");
    }

    private boolean getUseSingleRequestParameter() {
        return useSingleRequestParameter;
    }

    private void setUseSingleRequestParameter(boolean useSingleRequestParameter) {
        this.useSingleRequestParameter = useSingleRequestParameter;
    }

    private boolean getPrefixParameterInterfaces() {
        return prefixParameterInterfaces;
    }

    private void setPrefixParameterInterfaces(boolean prefixParameterInterfaces) {
        this.prefixParameterInterfaces = prefixParameterInterfaces;
    }

    private static boolean itemsAreUniqueId(CodegenProperty items) {
        if (items != null && items.items != null) {
            return itemsAreUniqueId(items.items);
        }
        if (items != null && items.vendorExtensions.get(X_IS_UNIQUE_ID) instanceof Boolean) {
            return Boolean.TRUE.equals(items.vendorExtensions.get(X_IS_UNIQUE_ID));
        }
        return false;
    }

    private static String getItemsDataType(CodegenProperty items) {
        if (items == null) {
            return null;
        }
        if (items.items != null) {
            return getItemsDataType(items.items);
        }
        return items.dataType;
    }

    public class ExtendedCodegenParameter extends CodegenParameter {
        public String dataTypeAlternate;
        public boolean isUniqueId; // this parameter represents a unique id (x-isUniqueId: true)
        public List<CodegenProperty> readOnlyVars; // a list of read-only properties
        public boolean hasReadOnly = false; // indicates the type has at least one read-only property

        public boolean itemsAreUniqueId() {
            return TypeScriptFetchClientCodegen.itemsAreUniqueId(this.items);
        }

        public String getItemsDataType() {
            return TypeScriptFetchClientCodegen.getItemsDataType(this.items);
        }

        public boolean isDateType() {
            return isDate && "Date".equals(dataType);
        }

        public boolean isDateTimeType() {
            return isDateTime && "Date".equals(dataType);
        }

        public ExtendedCodegenParameter(CodegenParameter cp) {
            super();

            this.isFormParam = cp.isFormParam;
            this.isQueryParam = cp.isQueryParam;
            this.isPathParam = cp.isPathParam;
            this.isHeaderParam = cp.isHeaderParam;
            this.isCookieParam = cp.isCookieParam;
            this.isBodyParam = cp.isBodyParam;
            this.isContainer = cp.isContainer;
            this.isCollectionFormatMulti = cp.isCollectionFormatMulti;
            this.isPrimitiveType = cp.isPrimitiveType;
            this.isModel = cp.isModel;
            this.isExplode = cp.isExplode;
            this.baseName = cp.baseName;
            this.paramName = cp.paramName;
            this.dataType = cp.dataType;
            this.datatypeWithEnum = cp.datatypeWithEnum;
            this.dataFormat = cp.dataFormat;
            this.contentType = cp.contentType;
            this.collectionFormat = cp.collectionFormat;
            this.description = cp.description;
            this.unescapedDescription = cp.unescapedDescription;
            this.baseType = cp.baseType;
            this.defaultValue = cp.defaultValue;
            this.enumName = cp.enumName;
            this.style = cp.style;
            this.nameInLowerCase = cp.nameInLowerCase;
            this.example = cp.example;
            this.jsonSchema = cp.jsonSchema;
            this.isString = cp.isString;
            this.isNumeric = cp.isNumeric;
            this.isInteger = cp.isInteger;
            this.isLong = cp.isLong;
            this.isNumber = cp.isNumber;
            this.isFloat = cp.isFloat;
            this.isDouble = cp.isDouble;
            this.isDecimal = cp.isDecimal;
            this.isByteArray = cp.isByteArray;
            this.isBinary = cp.isBinary;
            this.isBoolean = cp.isBoolean;
            this.isDate = cp.isDate;
            this.isDateTime = cp.isDateTime;
            this.isUuid = cp.isUuid;
            this.isUri = cp.isUri;
            this.isEmail = cp.isEmail;
            this.isFreeFormObject = cp.isFreeFormObject;
            this.isAnyType = cp.isAnyType;
            this.isArray = cp.isArray;
            this.isMap = cp.isMap;
            this.isFile = cp.isFile;
            this.isEnum = cp.isEnum;
            this.isEnumRef = cp.isEnumRef;
            this._enum = cp._enum;
            this.allowableValues = cp.allowableValues;
            this.items = cp.items;
            this.additionalProperties = cp.additionalProperties;
            this.vars = cp.vars;
            this.requiredVars = cp.requiredVars;
            this.mostInnerItems = cp.mostInnerItems;
            this.vendorExtensions = cp.vendorExtensions;
            this.hasValidation = cp.hasValidation;
            this.isNullable = cp.isNullable;
            this.required = cp.required;
            this.maximum = cp.maximum;
            this.exclusiveMaximum = cp.exclusiveMaximum;
            this.minimum = cp.minimum;
            this.exclusiveMinimum = cp.exclusiveMinimum;
            this.maxLength = cp.maxLength;
            this.minLength = cp.minLength;
            this.pattern = cp.pattern;
            this.maxItems = cp.maxItems;
            this.minItems = cp.minItems;
            this.uniqueItems = cp.uniqueItems;
            this.multipleOf = cp.multipleOf;
            this.setHasVars(cp.getHasVars());
            this.setHasRequired(cp.getHasRequired());
            this.setMaxProperties(cp.getMaxProperties());
            this.setMinProperties(cp.getMinProperties());
            setReadOnlyVars();
        }

        @Override
        public ExtendedCodegenParameter copy() {
            CodegenParameter superCopy = super.copy();
            ExtendedCodegenParameter output = new ExtendedCodegenParameter(superCopy);
            output.dataTypeAlternate = this.dataTypeAlternate;
            output.isUniqueId = this.isUniqueId;
            return output;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;

            if (this.getClass() != o.getClass())
                return false;

            boolean result = super.equals(o);
            ExtendedCodegenParameter that = (ExtendedCodegenParameter) o;
            return result &&
                    isUniqueId == that.isUniqueId &&
                    Objects.equals(dataTypeAlternate, that.dataTypeAlternate);
        }

        @Override
        public int hashCode() {
            int superHash = super.hashCode();
            return Objects.hash(superHash, dataTypeAlternate, isUniqueId);
        }

        @Override
        public String toString() {
            String superString = super.toString();
            final StringBuilder sb = new StringBuilder(superString);
            sb.append(", isUniqueId=").append(isUniqueId);
            sb.append(", dataTypeAlternate='").append(dataTypeAlternate).append('\'');
            return sb.toString();
        }

        private void setReadOnlyVars() {
            readOnlyVars = vars.stream().filter(v -> v.isReadOnly).collect(Collectors.toList());
            hasReadOnly = !readOnlyVars.isEmpty();
        }
    }

    public class ExtendedCodegenProperty extends CodegenProperty {
        public String dataTypeAlternate;
        public boolean isEntity; //Is a model containing an "id" property marked as isUniqueId and which matches the 'x-entityId' value.
        public boolean isUniqueId; // The property represents a unique id (x-isUniqueId: true)
        public boolean keepAsJSObject;
        public boolean isReservedRecordField;

        public boolean itemsAreUniqueId() {
            return TypeScriptFetchClientCodegen.itemsAreUniqueId(this.items);
        }

        public String getItemsDataType() {
            return TypeScriptFetchClientCodegen.getItemsDataType(this.items);
        }

        public boolean isDateType() {
            return isDate && "Date".equals(dataType);
        }

        public boolean isDateTimeType() {
            return isDateTime && "Date".equals(dataType);
        }

        public ExtendedCodegenProperty(CodegenProperty cp) {
            super();

            this.openApiType = cp.openApiType;
            this.baseName = cp.baseName;
            this.complexType = cp.complexType;
            this.getter = cp.getter;
            this.setter = cp.setter;
            this.description = cp.description;
            this.dataType = cp.dataType;
            this.datatypeWithEnum = cp.datatypeWithEnum;
            this.dataFormat = cp.dataFormat;
            this.name = cp.name;
            this.min = cp.min;
            this.max = cp.max;
            this.defaultValue = cp.defaultValue;
            this.defaultValueWithParam = cp.defaultValueWithParam;
            this.baseType = cp.baseType;
            this.containerType = cp.containerType;
            this.title = cp.title;
            this.unescapedDescription = cp.unescapedDescription;
            this.maxLength = cp.maxLength;
            this.minLength = cp.minLength;
            this.pattern = cp.pattern;
            this.example = cp.example;
            this.jsonSchema = cp.jsonSchema;
            this.minimum = cp.minimum;
            this.maximum = cp.maximum;
            this.multipleOf = cp.multipleOf;
            this.exclusiveMinimum = cp.exclusiveMinimum;
            this.exclusiveMaximum = cp.exclusiveMaximum;
            this.required = cp.required;
            this.deprecated = cp.deprecated;
            this.isPrimitiveType = cp.isPrimitiveType;
            this.isModel = cp.isModel;
            this.isContainer = cp.isContainer;
            this.isString = cp.isString;
            this.isNumeric = cp.isNumeric;
            this.isInteger = cp.isInteger;
            this.isLong = cp.isLong;
            this.isNumber = cp.isNumber;
            this.isFloat = cp.isFloat;
            this.isDouble = cp.isDouble;
            this.isDecimal = cp.isDecimal;
            this.isByteArray = cp.isByteArray;
            this.isBinary = cp.isBinary;
            this.isFile = cp.isFile;
            this.isBoolean = cp.isBoolean;
            this.isDate = cp.isDate; // full-date notation as defined by RFC 3339, section 5.6, for example, 2017-07-21
            this.isDateTime = cp.isDateTime; // the date-time notation as defined by RFC 3339, section 5.6, for example, 2017-07-21T17:32:28Z
            this.isUuid = cp.isUuid;
            this.isUri = cp.isUri;
            this.isEmail = cp.isEmail;
            this.isFreeFormObject = cp.isFreeFormObject;
            this.isAnyType = cp.isAnyType;
            this.isArray = cp.isArray;
            this.isMap = cp.isMap;
            this.isEnum = cp.isEnum;
            this.isEnumRef = cp.isEnumRef;
            this.isReadOnly = cp.isReadOnly;
            this.isWriteOnly = cp.isWriteOnly;
            this.isNullable = cp.isNullable;
            this.isSelfReference = cp.isSelfReference;
            this.isCircularReference = cp.isCircularReference;
            this.isDiscriminator = cp.isDiscriminator;
            this._enum = cp._enum;
            this.allowableValues = cp.allowableValues;
            this.items = cp.items;
            this.additionalProperties = cp.additionalProperties;
            this.vars = cp.vars;
            this.requiredVars = cp.requiredVars;
            this.mostInnerItems = cp.mostInnerItems;
            this.vendorExtensions = cp.vendorExtensions;
            this.hasValidation = cp.hasValidation;
            this.isInherited = cp.isInherited;
            this.discriminatorValue = cp.discriminatorValue;
            this.nameInLowerCase = cp.nameInLowerCase;
            this.nameInPascalCase = cp.nameInPascalCase;
            this.nameInSnakeCase = cp.nameInSnakeCase;
            this.enumName = cp.enumName;
            this.maxItems = cp.maxItems;
            this.minItems = cp.minItems;
            this.setMaxProperties(cp.getMaxProperties());
            this.setMinProperties(cp.getMinProperties());
            this.setUniqueItems(cp.getUniqueItems());
            this.isXmlAttribute = cp.isXmlAttribute;
            this.xmlPrefix = cp.xmlPrefix;
            this.xmlName = cp.xmlName;
            this.xmlNamespace = cp.xmlNamespace;
            this.isXmlWrapped = cp.isXmlWrapped;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;

            if (this.getClass() != o.getClass())
                return false;

            boolean result = super.equals(o);
            ExtendedCodegenProperty that = (ExtendedCodegenProperty) o;
            return result &&
                    isEntity == that.isEntity &&
                    isUniqueId == that.isUniqueId &&
                    keepAsJSObject == that.keepAsJSObject &&
                    isReservedRecordField == that.isReservedRecordField &&
                    Objects.equals(dataTypeAlternate, that.dataTypeAlternate);
        }

        @Override
        public int hashCode() {
            int superHash = super.hashCode();
            return Objects.hash(superHash, dataTypeAlternate, isEntity, isUniqueId, keepAsJSObject, isReservedRecordField);
        }

        @Override
        public String toString() {
            String superString = super.toString();
            final StringBuilder sb = new StringBuilder(superString);
            sb.append(", dataTypeAlternate='").append(dataTypeAlternate).append('\'');
            sb.append(", isEntity=").append(isEntity);
            sb.append(", isUniqueId=").append(isUniqueId);
            sb.append(", keepAsJSObject=").append(keepAsJSObject);
            sb.append(", isReservedRecordField=").append(isReservedRecordField);
            return sb.toString();
        }
    }

    public class ExtendedCodegenOperation extends CodegenOperation {
        boolean hasReturnPassthroughVoid, returnTypeSupportsEntities, returnTypeIsModel, returnTypeIsArray;
        String returnTypeAlternate, returnBaseTypeAlternate, returnPassthrough;

        public ExtendedCodegenOperation(CodegenOperation o) {
            super();

            this.responseHeaders.addAll(o.responseHeaders);
            this.hasAuthMethods = o.hasAuthMethods;
            this.hasConsumes = o.hasConsumes;
            this.hasProduces = o.hasProduces;
            this.hasOptionalParams = o.hasOptionalParams;
            this.returnTypeIsPrimitive = o.returnTypeIsPrimitive;
            this.returnSimpleType = o.returnSimpleType;
            this.subresourceOperation = o.subresourceOperation;
            this.isMap = o.isMap;
            this.isArray = o.isArray;
            this.isMultipart = o.isMultipart;
            this.isResponseBinary = o.isResponseBinary;
            this.isResponseFile = o.isResponseFile;
            this.isResponseOptional = o.isResponseOptional;
            this.hasReference = o.hasReference;
            this.isDeprecated = o.isDeprecated;
            this.isCallbackRequest = o.isCallbackRequest;
            this.uniqueItems = o.uniqueItems;
            this.path = o.path;
            this.operationId = o.operationId;
            this.returnType = o.returnType;
            this.returnFormat = o.returnFormat;
            this.httpMethod = o.httpMethod;
            this.returnBaseType = o.returnBaseType;
            this.returnContainer = o.returnContainer;
            this.summary = o.summary;
            this.unescapedNotes = o.unescapedNotes;
            this.notes = o.notes;
            this.baseName = o.baseName;
            this.defaultResponse = o.defaultResponse;
            this.discriminator = o.discriminator;
            this.consumes = o.consumes;
            this.produces = o.produces;
            this.prioritizedContentTypes = o.prioritizedContentTypes;
            this.servers = o.servers;
            this.bodyParam = o.bodyParam;
            this.allParams = o.allParams;
            this.bodyParams = o.bodyParams;
            this.pathParams = o.pathParams;
            this.queryParams = o.queryParams;
            this.headerParams = o.headerParams;
            this.formParams = o.formParams;
            this.cookieParams = o.cookieParams;
            this.requiredParams = o.requiredParams;
            this.optionalParams = o.optionalParams;
            this.authMethods = o.authMethods;
            this.tags = o.tags;
            this.responses = o.responses;
            this.callbacks = o.callbacks;
            this.imports = o.imports;
            this.examples = o.examples;
            this.requestBodyExamples = o.requestBodyExamples;
            this.externalDocs = o.externalDocs;
            this.vendorExtensions = o.vendorExtensions;
            this.nickname = o.nickname;
            this.operationIdOriginal = o.operationIdOriginal;
            this.operationIdLowerCase = o.operationIdLowerCase;
            this.operationIdCamelCase = o.operationIdCamelCase;
            this.operationIdSnakeCase = o.operationIdSnakeCase;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;

            if (this.getClass() != o.getClass())
                return false;

            boolean result = super.equals(o);
            ExtendedCodegenOperation that = (ExtendedCodegenOperation) o;
            return result &&
                    hasReturnPassthroughVoid == that.hasReturnPassthroughVoid &&
                    returnTypeSupportsEntities == that.returnTypeSupportsEntities &&
                    returnTypeIsArray == that.returnTypeIsArray &&
                    returnTypeIsModel == that.returnTypeIsModel &&
                    Objects.equals(returnTypeAlternate, that.returnTypeAlternate) &&
                    Objects.equals(returnBaseTypeAlternate, that.returnBaseTypeAlternate) &&
                    Objects.equals(returnPassthrough, that.returnPassthrough);
        }

        @Override
        public int hashCode() {
            int superHash = super.hashCode();
            return Objects.hash(superHash, returnPassthrough, hasReturnPassthroughVoid, returnTypeSupportsEntities, returnTypeIsArray, returnTypeIsModel, returnTypeAlternate, returnBaseTypeAlternate);
        }

        @Override
        public String toString() {
            String superString = super.toString();
            final StringBuilder sb = new StringBuilder(superString);
            sb.append(", hasReturnPassthroughVoid=").append(hasReturnPassthroughVoid);
            sb.append(", returnTypeSupportsEntities=").append(returnTypeSupportsEntities);
            sb.append(", returnTypeIsArray=").append(returnTypeIsArray);
            sb.append(", returnTypeIsModel=").append(returnTypeIsModel);
            sb.append(", returnTypeAlternate='").append(returnTypeAlternate).append('\'');
            sb.append(", returnBaseTypeAlternate='").append(returnBaseTypeAlternate).append('\'');
            sb.append(", returnPassthrough='").append(returnPassthrough).append('\'');
            return sb.toString();
        }
    }

    public class ExtendedCodegenModel extends CodegenModel {
        @Getter @Setter
        public Set<String> modelImports = new TreeSet<String>();

        // oneOfModels, oneOfArrays & oneOfPrimitives contain a list of types used in schemas
        // composed with oneOf and are used to define the import list and the to & from
        // 'TypedJSON' conversion methods in the composed model classes.
        @Getter @Setter
        public Set<String> oneOfModels = new TreeSet<>();
        @Getter @Setter
        public Set<String> oneOfArrays = new TreeSet<>();
        @Getter @Setter
        public Set<CodegenProperty> oneOfPrimitives = new HashSet<>();

        public boolean isEntity; // Is a model containing an "id" property marked as isUniqueId
        public String returnPassthrough;
        public boolean hasReturnPassthroughVoid;

        public boolean isDateType() {
            return isDate && "Date".equals(dataType);
        }

        public boolean isDateTimeType() {
            return isDateTime && "Date".equals(dataType);
        }

        public ExtendedCodegenModel(CodegenModel cm) {
            super();

            this.parent = cm.parent;
            this.parentSchema = cm.parentSchema;
            this.interfaces = cm.interfaces;
            this.allParents = cm.allParents;
            this.parentModel = cm.parentModel;
            this.interfaceModels = cm.interfaceModels;
            this.children = cm.children;
            this.anyOf = cm.anyOf;
            this.oneOf = cm.oneOf;
            this.allOf = cm.allOf;
            this.name = cm.name;
            this.classname = cm.classname;
            this.title = cm.title;
            this.description = cm.description;
            this.classVarName = cm.classVarName;
            this.modelJson = cm.modelJson;
            this.dataType = cm.dataType;
            this.xmlPrefix = cm.xmlPrefix;
            this.xmlNamespace = cm.xmlNamespace;
            this.xmlName = cm.xmlName;
            this.classFilename = cm.classFilename;
            this.unescapedDescription = cm.unescapedDescription;
            this.discriminator = cm.discriminator;
            this.defaultValue = cm.defaultValue;
            this.arrayModelType = cm.arrayModelType;
            this.isAlias = cm.isAlias;
            this.isString = cm.isString;
            this.isInteger = cm.isInteger;
            this.isLong = cm.isLong;
            this.isNumber = cm.isNumber;
            this.isNumeric = cm.isNumeric;
            this.isFloat = cm.isFloat;
            this.isDouble = cm.isDouble;
            this.isDate = cm.isDate;
            this.isDateTime = cm.isDateTime;
            this.vars = cm.vars;
            this.allVars = cm.allVars;
            this.requiredVars = cm.requiredVars;
            this.optionalVars = cm.optionalVars;
            this.hasReadOnly = cm.hasReadOnly;
            this.readOnlyVars = cm.readOnlyVars;
            this.readWriteVars = cm.readWriteVars;
            this.parentVars = cm.parentVars;
            this.allowableValues = cm.allowableValues;
            this.mandatory = cm.mandatory;
            this.allMandatory = cm.allMandatory;
            this.imports = cm.imports;
            this.hasVars = cm.hasVars;
            this.emptyVars = cm.emptyVars;
            this.hasMoreModels = cm.hasMoreModels;
            this.hasEnums = cm.hasEnums;
            this.isEnum = cm.isEnum;
            this.isNullable = cm.isNullable;
            this.hasRequired = cm.hasRequired;
            this.hasOptional = cm.hasOptional;
            this.isArray = cm.isArray;
            this.hasChildren = cm.hasChildren;
            this.isMap = cm.isMap;
            this.isDeprecated = cm.isDeprecated;
            this.hasOnlyReadOnly = cm.hasOnlyReadOnly;
            this.externalDocumentation = cm.externalDocumentation;

            this.vendorExtensions = cm.vendorExtensions;
            this.additionalPropertiesType = cm.additionalPropertiesType;
            this.isAdditionalPropertiesTrue = cm.isAdditionalPropertiesTrue;
            this.setMaxProperties(cm.getMaxProperties());
            this.setMinProperties(cm.getMinProperties());
            this.setUniqueItems(cm.getUniqueItems());
            this.setMaxItems(cm.getMaxItems());
            this.setMinItems(cm.getMinItems());
            this.setMaxLength(cm.getMaxLength());
            this.setMinLength(cm.getMinLength());
            this.setExclusiveMinimum(cm.getExclusiveMinimum());
            this.setExclusiveMaximum(cm.getExclusiveMaximum());
            this.setMinimum(cm.getMinimum());
            this.setMaximum(cm.getMaximum());
            this.setPattern(cm.getPattern());
            this.setMultipleOf(cm.getMultipleOf());
            this.setItems(cm.getItems());
            this.setAdditionalProperties(cm.getAdditionalProperties());
            this.setIsModel(cm.getIsModel());
            this.setComposedSchemas(cm.getComposedSchemas());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null)
                return false;

            if (this.getClass() != o.getClass())
                return false;

            boolean result = super.equals(o);
            ExtendedCodegenModel that = (ExtendedCodegenModel) o;
            return result &&
                    isEntity == that.isEntity &&
                    hasReturnPassthroughVoid == that.hasReturnPassthroughVoid &&
                    Objects.equals(returnPassthrough, that.returnPassthrough) &&
                    Objects.equals(modelImports, that.modelImports);

        }

        @Override
        public int hashCode() {
            int superHash = super.hashCode();
            return Objects.hash(superHash, isEntity, returnPassthrough, hasReturnPassthroughVoid, getModelImports());
        }

        @Override
        public String toString() {
            String superString = super.toString();
            final StringBuilder sb = new StringBuilder(superString);
            sb.append(", modelImports=").append(modelImports);
            sb.append(", isEntity=").append(isEntity);
            sb.append(", returnPassthrough='").append(returnPassthrough).append('\'');
            sb.append(", hasReturnPassthroughVoid=").append(hasReturnPassthroughVoid);
            return sb.toString();
        }
    }

    @Override
    protected String getLicenseNameDefaultValue() {
        return null;
    }
}
