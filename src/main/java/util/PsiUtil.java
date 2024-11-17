package util;

import com.intellij.lang.ecmascript6.psi.impl.ES6FieldStatementImpl;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptParameter;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;

public class PsiUtil {

    private static final Logger LOG = Logger.getInstance(PsiUtil.class);

    public static ES6FieldStatementImpl createJSFieldStatement(JSClass context, String name, JSType type, String visibility) {
        String fieldText = visibility + " " + name + " : " + type.getTypeText() + ";";

        StringBuilder classCode = new StringBuilder();
        classCode.append("class PsiUtilTemp {\n");
        classCode.append(fieldText);
        classCode.append("}\n");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, classCode);
        TypeScriptClass psiClass = PsiTreeUtil.getChildOfType(psiFile, TypeScriptClass.class);

        return PsiTreeUtil.getChildOfType(psiClass, ES6FieldStatementImpl.class);
    }

    public static TypeScriptParameter createTypeScriptParameter(PsiElement context, String name, JSType type) {
        String parameterText = name + " : " + type.getTypeText();

        StringBuilder functionCode = new StringBuilder();
        functionCode.append("function psiUtilTemp(");
        functionCode.append(parameterText);
        functionCode.append(") {}");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, functionCode);
        TypeScriptFunction psiFunction = PsiTreeUtil.getChildOfType(psiFile, TypeScriptFunction.class);

        return (TypeScriptParameter) psiFunction.getParameters()[0];
    }

    public static void addParameterToParameterList(TypeScriptParameter parameter, JSParameterList parameterList) {

        JSParameterListElement[] listElements = parameterList.getParameters();
        TypeScriptParameter[] parameters = new TypeScriptParameter[listElements.length + 1];

        for (int i = 0; i < listElements.length; i++) {
            parameters[i] = (TypeScriptParameter) listElements [i];
        }
        parameters[parameters.length - 1] = parameter;

        JSParameterList newParameterList = createJSParameterList(parameterList, parameters);
        WriteCommandAction.runWriteCommandAction(parameterList.getProject(), () -> {
            parameterList.replace(newParameterList);
        });


    }

    public static JSParameterList createJSParameterList(PsiElement context, TypeScriptParameter[] parameters) {

        StringBuilder functionCode = new StringBuilder();
        functionCode.append("function psiUtilTemp(");
        for (TypeScriptParameter parameter : parameters) {
            functionCode.append(parameter.getText());
            functionCode.append(",");
        }
        functionCode.deleteCharAt(functionCode.length() - 1);
        functionCode.append(") {}");

        PsiFile psiFile = PsiFileFactory.getInstance(context.getProject()).createFileFromText( "PsiUtilTemp.ts", TypeScriptFileType.INSTANCE, functionCode);
        TypeScriptFunction psiFunction = PsiTreeUtil.getChildOfType(psiFile, TypeScriptFunction.class);

        return psiFunction.getParameterList();
    }

}
