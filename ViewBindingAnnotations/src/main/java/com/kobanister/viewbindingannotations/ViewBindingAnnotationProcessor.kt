package com.kobanister.viewbindingannotations

import com.kobanister.viewbindingannotations.annotation.BindActivity
import com.kobanister.viewbindingannotations.annotation.BindFragment
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic

/**
 * Generates BindingFactory.
 *
 * In order to debug this annotation processor:
 * New remote configuration with default parameters (Run -> Edit Configurations) should be created.
 *
 * The following command should be ran in the terminal:
 * gradlew :app:clean :app:compileDevDebugKotlin --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy="in-process" -Dkotlin.daemon.jvm.options="-Xdebug,-Xrunjdwp:transport=dt_socket\,address=5005\,server=y\,suspend=n"
 * NOTE: the command uses gradlew file, on the Linux OS you may be required to give executive
 * permission to it (chmode +x gradlew) in order to work properly
 *
 * After this command will be executed the daemon would suspend until debugger will start.
 * Debugging with the remote configuration will allow to debug annotation processor.
 * */
class ViewBindingAnnotationProcessor : AbstractProcessor() {

    private val fragmentAnnotation = BindFragment::class.java
    private val activityAnnotation = BindActivity::class.java

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.isEmpty()) return false
        val fragmentsClassNames = roundEnv.getClassNameMap(fragmentAnnotation)
        val activitiesClassNames = roundEnv.getClassNameMap(activityAnnotation)

        generateBingingFactory(fragmentsClassNames, activitiesClassNames)
        return true
    }

    private fun RoundEnvironment.getClassNameMap(annotation: Class<out Annotation>): MutableMap<TypeName, ClassName> {
        val classNameMap = mutableMapOf<TypeName, ClassName>()
        getElementsAnnotatedWith(annotation).forEach { annotatedElement ->
            val viewOwnerName = (annotatedElement as TypeElement).getViewOwnerName()
            val binderClassName = annotatedElement.getBindingName()
            classNameMap[viewOwnerName] = binderClassName
        }
        return classNameMap
    }

    private fun TypeElement.getViewOwnerName(): TypeName {
        val className = this.asClassName()

        return this.typeParameters.size.takeIf { it > 0 }?.let { genericsSize ->
            val typeArguments = Array(genericsSize) { STAR }
            className.parameterizedBy(typeArguments.toList())
        } ?: className
    }

    private fun Element.getBindingName(): ClassName = try {
        getSuperclass()
            .getGenerics()
            .first { generic -> generic.implementsInterface(VIEWBINDING_INTERFACE) }
            .asClassName()
    } catch (ex: NoSuchElementException) {
        val errorMsg = "\n${this.simpleName} should have parent with <T: ViewBinding> generic\n"
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, errorMsg, this)
        throw IllegalArgumentException(errorMsg)
    }

    private fun generateBingingFactory(
        fragmentsClassNames: MutableMap<TypeName, ClassName>,
        activitiesClassNames: MutableMap<TypeName, ClassName>
    ) {
        val kaptKotlinGeneratedDir =
            processingEnv.options[KOTLIN_DIRECTORY_NAME] ?: throw Throwable("Directory cannot be null")

        val typeSpec = TypeSpec.objectBuilder(FACTORY_NAME)
            .addBindingFunction(fragmentsClassNames, GetBindingInput.Fragment)
            .addBindingFunction(activitiesClassNames, GetBindingInput.Activity)
            .build()

        FileSpec.builder("factory", FACTORY_NAME)
            .addType(typeSpec)
            .build()
            .writeTo(File(kaptKotlinGeneratedDir))
    }

    private fun TypeSpec.Builder.addBindingFunction(
        classNames: MutableMap<TypeName, ClassName>,
        input: GetBindingInput
    ): TypeSpec.Builder =
        addFunction(
            FunSpec.builder("getBinding")
                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("\"UNCHECKED_CAST\"").build())
                .addTypeVariable(TypeVariableName.invoke("VB"))
                .addParameter("viewOwner", input.className)
                .addParameter("container", ClassName("android.view", "ViewGroup").copy(true))
                .returns(TypeVariableName.invoke("VB"))
                .addFactoryStatement(classNames)
                .build()
        )

    private fun FunSpec.Builder.addFactoryStatement(
        classNameMap: MutableMap<TypeName, ClassName>
    ): FunSpec.Builder = this
        .addCode("return when (viewOwner) {\n")
        .addCode(
            "%L",
            buildCodeBlock {
                classNameMap.forEach { (viewOwnerType, binding) ->
                    add("    is·%T·->·%T.inflate(viewOwner.layoutInflater,·container,·false)\n", viewOwnerType, binding)
                }
            }
        )
        .addCode("    else -> throw kotlin.Throwable(\"Binder not found for \$viewOwner\")\n")
        .addCode("} as VB\n")

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(fragmentAnnotation.canonicalName, activityAnnotation.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    private fun Element.getSuperclass(): DeclaredType = ((this as TypeElement).superclass as DeclaredType)

    private fun DeclaredType.getGenerics(): List<TypeElement> = typeArguments.filterIsInstance<DeclaredType>()
        .map { it.asElement() }
        .filterIsInstance<TypeElement>()

    private fun TypeElement.implementsInterface(input: String): Boolean =
        interfaces.find { it.toString() == input } != null

    private enum class GetBindingInput(val className: ClassName) {
        Activity(ClassName("androidx.fragment.app", "FragmentActivity")),
        Fragment(ClassName("androidx.fragment.app", "Fragment"))
    }

    companion object {
        private const val KOTLIN_DIRECTORY_NAME = "kapt.kotlin.generated"
        private const val FACTORY_NAME = "BindingFactory"
        private const val VIEWBINDING_INTERFACE = "androidx.viewbinding.ViewBinding"
    }
}
