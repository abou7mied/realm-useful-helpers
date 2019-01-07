package com.github.abou7mied.realm_useful_helpers;


import com.google.auto.service.AutoService;
import com.google.gson.annotations.SerializedName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import io.realm.annotations.Ignore;
import io.realm.annotations.RealmClass;

@AutoService(Processor.class)
public class CustomRealmAnnotationProcessor extends AbstractProcessor {

    private static final String CLASS_SUFFIX = "Fields";
    private static final char CHAR_DOT = '.';
    private static final String PACKAGE_NAME = "io.realm.fields";

    private Messager messager;
    private Filer filer;
    private static final String JSON_TO_REALM_MAP_NAME = "jsonToRealmMap";
    private Elements elementUtils;
    //    private HashMap<String, TypeSpec.Builder> typeSpecBuilders = new HashMap<>();
//    private HashMap<String, TypeSpec.Builder> subTypeSpecBuilders = new HashMap<>();
//    private HashMap<String, HashMap<String, ArrayList<?>>> models = new HashMap<>();
    private HashMap<String, ArrayList<ModelFieldSpec>> modelFields = new HashMap<>();
    private HashMap<String, ArrayList<String[]>> modelSubClasses = new HashMap<>();
    private Types typeUtils;
    private TypeMirror realmModelInterface;
    private DeclaredType realmListClass;
    private DeclaredType realmResultsClass;
    private TypeSpec.Builder realmGsonClassBuilder;
    private CodeBlock.Builder codeBlock;
    private ParameterizedTypeName parameterizedTypeName;
    private TypeSpec mapReferenceType;
    private TypeSpec mapSubPolicyEnum;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();

        realmModelInterface = elementUtils.getTypeElement("io.realm.RealmModel").asType();
        realmListClass = typeUtils.getDeclaredType(elementUtils.getTypeElement("io.realm.RealmList"),
                typeUtils.getWildcardType(null, null));
        realmResultsClass = typeUtils.getDeclaredType(elementUtils.getTypeElement("io.realm.RealmResults"),
                typeUtils.getWildcardType(null, null));

        filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(RealmClass.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {


        try {

            parameterizedTypeName = ParameterizedTypeName.get(ClassName.get(HashMap.class), ClassName.get(String.class), ClassName.get(Object.class));
            TypeName hashMapTypeName = ParameterizedTypeName.get(ClassName.get(HashMap.class), ClassName.get(Class.class), parameterizedTypeName);

            FieldSpec gsonToRealmMap = FieldSpec.builder(hashMapTypeName, JSON_TO_REALM_MAP_NAME)
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T()", hashMapTypeName)
                    .build();

            realmGsonClassBuilder = TypeSpec
                    .classBuilder("RealmJsonMapping")
                    .addField(gsonToRealmMap)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL);


            final String classParamName = "cls";
            final String keyParamName = "key";
            final String getRealmNameMethod = "getRealmName";
            final String mapJsonObjectToRealmMethod = "mapJsonObjectToRealm";
            final String mapJsonArrayToRealmMethod = "mapJsonArrayToRealm";
            final String getNameOfMethod = "getNameOf";


            mapReferenceType = TypeSpec.classBuilder("MapReference").
                    addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addField(String.class, "key")
                    .addField(Class.class, "cls")
                    .addMethod(
                            MethodSpec.constructorBuilder()
                                    .addParameter(String.class, "key")
                                    .addParameter(Class.class, "cls")
                                    .addStatement("this.key = key")
                                    .addStatement("this.cls = cls")
                                    .build()
                    )

                    .addMethod(
                            MethodSpec.methodBuilder("getKey")
                                    .addStatement("return key")
                                    .returns(String.class)
                                    .build()
                    )
                    .addMethod(
                            MethodSpec.methodBuilder("getCls")
                                    .addStatement("return cls")
                                    .returns(Class.class)
                                    .build()
                    )
                    .addMethod(
                            MethodSpec.methodBuilder("getHashMap")
                                    .addStatement("return $L.get(cls)", JSON_TO_REALM_MAP_NAME)
                                    .returns(parameterizedTypeName)
                                    .build()
                    )
                    .build();

            mapSubPolicyEnum = TypeSpec.enumBuilder("MapSubPolicy")
                    .addEnumConstant("ASSIGN_ONLY")
                    .addEnumConstant("ASSIGN_AND_MAP")
                    .addEnumConstant("IGNORE")
                    .build();

            MethodSpec getRealmNameMethodSpec = MethodSpec
                    .methodBuilder(getRealmNameMethod)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(String.class)
                    .addParameter(Class.class, classParamName)
                    .addParameter(String.class, keyParamName)
                    .addStatement("$T realmName = null", String.class)
                    .beginControlFlow("if ($L.containsKey($L))", JSON_TO_REALM_MAP_NAME, classParamName)
                    .addStatement("$T map = $L.get($L)", parameterizedTypeName, JSON_TO_REALM_MAP_NAME, classParamName)
                    .addStatement("realmName = $L($L, map)", getNameOfMethod, keyParamName)
                    .endControlFlow()
                    .addStatement("return realmName")
                    .build();


            ParameterizedTypeName setString = ParameterizedTypeName.get(Set.class, String.class);

            MethodSpec mapJsonObjectToRealmBaseMethodSpec = MethodSpec
                    .methodBuilder(mapJsonObjectToRealmMethod)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addException(JSONException.class)
                    .returns(JSONObject.class)
                    .addParameter(Class.class, classParamName)
                    .addParameter(JSONObject.class, "object")
                    .addStatement("return $L($L, object, MapSubPolicy.ASSIGN_ONLY)", mapJsonObjectToRealmMethod, classParamName)
                    .build();


            MethodSpec mapJsonObjectToRealmMethodSpec = MethodSpec
                    .methodBuilder(mapJsonObjectToRealmMethod)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addException(JSONException.class)
                    .returns(JSONObject.class)
                    .addParameter(Class.class, classParamName)
                    .addParameter(JSONObject.class, "object")
                    .addParameter(ClassName.bestGuess(mapSubPolicyEnum.name), "mapSubPolicy")
                    .addStatement("$T mappedObject = new $T()", JSONObject.class, JSONObject.class)
                    .beginControlFlow(" if ($L.containsKey($L))", JSON_TO_REALM_MAP_NAME, classParamName)
                    .addStatement("$T fieldsMap = $L.get($L);", parameterizedTypeName, JSON_TO_REALM_MAP_NAME, classParamName)
                    .addStatement("$T keySet = fieldsMap.keySet()", setString)
                    .beginControlFlow("for (String setKey : keySet)")
                    .addStatement("$T mappedKeyObject = fieldsMap.get(setKey)", Object.class)
                    .beginControlFlow("if (object.has(setKey))")
                    .addStatement("$T value = object.get(setKey)", Object.class)
                    .beginControlFlow("if(mappedKeyObject instanceof String)")
                    .addStatement("String mappedKey = (String) mappedKeyObject")
                    .addStatement("mappedObject.put(mappedKey, value)")
                    .nextControlFlow("else")
                    .addStatement("MapReference mapReference = (MapReference) mappedKeyObject")
                    .addStatement("String mappedKey = mapReference.getKey()")
                    .beginControlFlow("switch (mapSubPolicy)")
                    .addStatement("case ASSIGN_ONLY: mappedObject.put(mappedKey, value); break")
                    .beginControlFlow("case ASSIGN_AND_MAP:")
                    .beginControlFlow("if (value instanceof JSONObject)")
                    .addStatement("mappedObject.put(mappedKey, $L(mapReference.getCls(), (JSONObject) value, mapSubPolicy))", mapJsonObjectToRealmMethod)
                    .nextControlFlow("else if (value instanceof JSONArray)")
                    .addStatement("mappedObject.put(mappedKey, $L(mapReference.getCls(), (JSONArray) value, mapSubPolicy))", mapJsonArrayToRealmMethod)
                    .addStatement("break")
                    .endControlFlow()
                    .addStatement("break")
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return mappedObject")
                    .build();

            /*
            for (String setKey : keySet) {
                if (object.has(setKey)) {
                    if (mappedKeyObject instanceof String) {
                        String mappedKey = (String) mappedKeyObject;
                        mappedObject.put(mappedKey, value);
                    } else {
                        MapReference mapReference = (MapReference) mappedKeyObject;
                        String mappedKey = mapReference.getKey();
                        switch (mapSubPolicy) {
                            case ASSIGN_ONLY: mappedObject.put(mappedKey, value); break;
                            case ASSIGN_AND_MAP:
                                if (value instanceof JSONObject)
                                    mappedObject.put(mappedKey, mapJsonObjectToRealm(mapReference.getCls(), (JSONObject) value, mapSubPolicy));
                                else if (value instanceof JSONArray)
                                    mappedObject.put(mappedKey, mapGsonArrayToRealm(mapReference.getCls(), (JSONArray) value, mapSubPolicy));
                                break;
                        }
                    }
                }
            * */


            MethodSpec mapJsonArrayToRealmBaseMethodSpec = MethodSpec
                    .methodBuilder(mapJsonArrayToRealmMethod)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addException(JSONException.class)
                    .returns(JSONArray.class)
                    .addParameter(Class.class, classParamName)
                    .addParameter(JSONArray.class, "array")
                    .addStatement("return $L($L, array, MapSubPolicy.ASSIGN_ONLY)", mapJsonArrayToRealmMethod, classParamName)
                    .build();

            MethodSpec mapJsonArrayToRealmMethodSpec = MethodSpec
                    .methodBuilder(mapJsonArrayToRealmMethod)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addException(JSONException.class)
                    .returns(JSONArray.class)
                    .addParameter(Class.class, classParamName)
                    .addParameter(JSONArray.class, "array")
                    .addParameter(ClassName.bestGuess(mapSubPolicyEnum.name), "mapSubPolicy")
                    .addStatement("$T mappedArray = new $T()", JSONArray.class, JSONArray.class)
                    .beginControlFlow("for (int i = 0; i < array.length(); i++)")
                    .addStatement("mappedArray.put($L($L, ($T) array.get(i), mapSubPolicy))", mapJsonObjectToRealmMethod, classParamName, JSONObject.class)
                    .endControlFlow()
                    .addStatement("return mappedArray")
                    .build();


            MethodSpec getNameOfMethodSpec = MethodSpec
                    .methodBuilder(getNameOfMethod)
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(String.class)
                    .addParameter(String.class, "key")
                    .addParameter(parameterizedTypeName, "map")
                    .addStatement("String name = null")
                    .addStatement("String[] subKeys = key.split(\"\\\\.\")")
                    .addStatement("final String baseKey = subKeys[0]")
                    .addStatement("final Object value = map.get(baseKey)")
                    .beginControlFlow("if(value instanceof String)")
                    .addStatement("name = (String) value")
                    .nextControlFlow("else if (value instanceof MapReference)")
                    .addStatement("MapReference mapReference = (MapReference) value")
                    .addStatement("name = mapReference.getKey()")
                    .beginControlFlow("if (subKeys.length > 1)")
                    .addStatement("final String subName = getNameOf(String.join(\".\", subKeys).replace(baseKey + \".\", \"\"), mapReference.getHashMap())")
                    .addStatement("name += \".\"")
                    .beginControlFlow("if (subName != null)")
                    .addStatement("name += subName")
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return name")
                    .build();


            realmGsonClassBuilder.addType(mapSubPolicyEnum);
            realmGsonClassBuilder.addType(mapReferenceType);
            realmGsonClassBuilder.addMethod(mapJsonObjectToRealmBaseMethodSpec);
            realmGsonClassBuilder.addMethod(getRealmNameMethodSpec);
            realmGsonClassBuilder.addMethod(mapJsonObjectToRealmMethodSpec);
            realmGsonClassBuilder.addMethod(mapJsonArrayToRealmBaseMethodSpec);
            realmGsonClassBuilder.addMethod(mapJsonArrayToRealmMethodSpec);
            realmGsonClassBuilder.addMethod(getNameOfMethodSpec);


            codeBlock = CodeBlock.builder()
                    .addStatement("$T map", this.parameterizedTypeName);


            for (Element element : roundEnv.getElementsAnnotatedWith(RealmClass.class)) {
                if (element.getKind() != ElementKind.CLASS) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Can be applied to classes only.");
                    return true;
                }

                TypeElement typeElement = ((TypeElement) element);

                String qualifiedName = typeElement.getQualifiedName().toString();
//                String packageName = getPackage(qualifiedName);
                String className = getClassName(qualifiedName);


                for (Element elm : element.getEnclosedElements()) {
                    if (elm.getKind() != ElementKind.FIELD
                            || elm.getModifiers().contains(Modifier.TRANSIENT)
                            || elm.getModifiers().contains(Modifier.STATIC)
                            || elm.getAnnotation(Ignore.class) != null

                            ) {
                        continue;
                    }


                    final TypeMirror elmType = elm.asType();
                    final boolean isList = typeUtils.isAssignable(elmType, realmListClass)
                            || typeUtils.isAssignable(elmType, realmResultsClass);

                    final boolean isModelOrCollection =
                            typeUtils.isAssignable(elmType, realmModelInterface)
                                    || isList;
                    String fieldName = elm.getSimpleName().toString();
                    String newFieldName = CaseFormat.LOWER_CAMEL.convert(CaseFormat.UPPER_UNDERSCORE, fieldName);
                    String targetModelName = null;

                    if (isModelOrCollection) {
//                        final TypeSpec.Builder builder = initSubClassBuilder(className + CHAR_DOT + newFieldName);
//                        builder.addField(createFieldSpec("$", fieldName));
                        final ArrayList<String[]> subClasses = initModelSubClasses(className);
                        final TypeMirror targetElm = isList ? elementUtils.getTypeElement(((ParameterizedTypeName) ParameterizedTypeName.get(elmType)).typeArguments.get(0).toString()).asType() : elmType;
                        targetModelName = getClassName(targetElm.toString());
                        subClasses.add(new String[]{fieldName, newFieldName, targetModelName});

                    }

                    String serializedNameValue = null;
                    SerializedName serializedNameAnnotation = elm.getAnnotation(SerializedName.class);
                    if (serializedNameAnnotation != null) {
                        serializedNameValue = serializedNameAnnotation.value();
//                            cb.addStatement("map.put($S, $T.$L)", serializedNameValue, newClassName, newFieldName);
                    }

                    final ArrayList<ModelFieldSpec> modelFields = initModelFields(className);
                    final ModelFieldSpec modelFieldSpec = new ModelFieldSpec(newFieldName, fieldName, serializedNameValue, targetModelName);
                    modelFields.add(modelFieldSpec);


                }

//                final ClassName newClassName = ClassName.get(PACKAGE_NAME, classNameWithSuffix);
//                if (cb != null) {
//                    codeBlock.addStatement("map = new $T()", parameterizedTypeName);
//                    codeBlock.add(cb.build());
//                    codeBlock.addStatement("$L.put($T.class, map)", JSON_TO_REALM_MAP_NAME, newClassName);
//                }

//                if (fields.size() > 0) {
//                    final TypeSpec.Builder classBuilder = initClassBuilder(className);
//                    for (FieldSpec field : fields)
//                        classBuilder.addField(field);
//                }


            }

            final Set<Map.Entry<String, ArrayList<ModelFieldSpec>>> entries = modelFields.entrySet();

            for (Map.Entry<String, ArrayList<ModelFieldSpec>> entry : entries) {
                final String modelName = entry.getKey();
                final TypeSpec.Builder builder = initClassBuilder(modelName, CLASS_SUFFIX);
                addFieldsToClass(builder, modelName + CLASS_SUFFIX, entry.getValue(), "");


                if (modelSubClasses.containsKey(modelName)) {
                    final ArrayList<String[]> subClasses = modelSubClasses.get(modelName);
                    for (String[] subClassNames : subClasses) {
                        final TypeSpec.Builder subClassBuilder = initClassBuilder(subClassNames[1], "");
                        subClassBuilder.addModifiers(Modifier.STATIC);
                        subClassBuilder.addField(createFieldSpec("$", subClassNames[0]));
                        final ArrayList<ModelFieldSpec> subModelFieldSpecs = modelFields.get(subClassNames[2]);
                        addFieldsToClass(subClassBuilder, modelName + CLASS_SUFFIX, subModelFieldSpecs, subClassNames[0] + ".");
                        builder.addType(subClassBuilder.build());
                    }
                }
                JavaFile.builder(PACKAGE_NAME, builder.build()).build().writeTo(filer);
            }

            realmGsonClassBuilder.addStaticBlock(codeBlock.build());
            JavaFile.builder(PACKAGE_NAME, realmGsonClassBuilder.build()).build().writeTo(filer);

        } catch (Exception e) {
            e.printStackTrace();
        }


        return true;
    }

    private void addFieldsToClass(TypeSpec.Builder builder, String classNameWithSuffix, ArrayList<ModelFieldSpec> modelFieldSpecs, String prefix) {
        final ClassName newClassName = ClassName.get(PACKAGE_NAME, classNameWithSuffix);
        boolean inInnerClass = !prefix.equals("");
        if (!inInnerClass)
            codeBlock.addStatement("map = new $T()", parameterizedTypeName);

        for (ModelFieldSpec modelFieldSpec : modelFieldSpecs) {

            if (!modelFieldSpec.hasModelName())
                builder.addField(createFieldSpec(modelFieldSpec.getName(), prefix + modelFieldSpec.getValue()));

            final String serializedNameValue = modelFieldSpec.getSerializedNameValue();
            if (serializedNameValue != null && !inInnerClass) {
                if (modelFieldSpec.hasModelName()) {
                    codeBlock.addStatement("map.put($S, new $T($T.$L.$L, $L.class))", serializedNameValue, ClassName.bestGuess(mapReferenceType.name), newClassName, modelFieldSpec.getName(), "$", modelFieldSpec.getModelName() + CLASS_SUFFIX);
                } else
                    codeBlock.addStatement("map.put($S, $T.$L)", serializedNameValue, newClassName, modelFieldSpec.getName());
            }

        }
        if (!inInnerClass)
            codeBlock.addStatement("$L.put($T.class, map)", JSON_TO_REALM_MAP_NAME, newClassName);
    }

    private FieldSpec createFieldSpec(String key, String value) {
        return FieldSpec.builder(String.class, key)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", value)
                .build();
    }


    private ArrayList<ModelFieldSpec> initModelFields(String model) {
        if (!modelFields.containsKey(model)) {
            modelFields.put(model, new ArrayList<ModelFieldSpec>());
        }
        return modelFields.get(model);
    }

    private ArrayList<String[]> initModelSubClasses(String model) {
        if (!modelSubClasses.containsKey(model)) {
            modelSubClasses.put(model, new ArrayList<String[]>());
        }
        return modelSubClasses.get(model);
    }


    private TypeSpec.Builder initClassBuilder(String className, String suffix) {
        return TypeSpec.classBuilder(className + suffix)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
    }

//
//    private HashMap<String, ArrayList<?>> initClassBuilder(String className) {
//        if (!models.containsKey(className)) {
//            HashMap<String, ArrayList<?>> model = new HashMap<>();
//            final HashMap<String, ArrayList<?>> hashMap = new HashMap<>();
//            hashMap.put("fields", new ArrayList<>());
//            hashMap.put("subclasses", new ArrayList<>());
//            models.put(className, hashMap);
////            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
////                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
////            typeSpecBuilders.put(className, classBuilder);
//        }
//        return models.get(className);
//    }

//    private TypeSpec.Builder initSubClassBuilder(String className) {
//        if (!subTypeSpecBuilders.containsKey(className)) {
//            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(getClassName(className))
//                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);
//            subTypeSpecBuilders.put(className, classBuilder);
//        }
//        return subTypeSpecBuilders.get(className);
//    }


    private String getPackage(String qualifier) {
        return qualifier.substring(0, qualifier.lastIndexOf(CHAR_DOT));
    }

    private String getClassName(String qualifier) {
        return qualifier.substring(qualifier.lastIndexOf(CHAR_DOT) + 1);
    }


    private static class ModelFieldSpec {

        private String name;
        private String value;
        private final String serializedNameValue;
        private String modelName;


        ModelFieldSpec(String name, String value, String serializedNameValue, String modelName) {
            this.name = name;
            this.value = value;
            this.serializedNameValue = serializedNameValue;
            this.modelName = modelName;
        }

        String getName() {
            return name;
        }


        String getValue() {
            return value;
        }

        String getSerializedNameValue() {
            return serializedNameValue;
        }

        String getModelName() {
            return modelName;
        }

        boolean hasModelName() {
            return getModelName() != null;
        }
    }
}