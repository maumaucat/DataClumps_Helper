package util;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField;
import com.intellij.psi.PsiField;


public class ClassField extends Property {

    private final String visibility;

    public ClassField(TypeScriptField field) {
        super(field.getName(), field.getJSType());
        this.visibility = field.getAccessType().toString();
    }

    @Override
    public int hashCode() {
        return super.hashCode() + visibility.hashCode();
    }

    public String getVisibility() {
        return visibility;
    }


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ClassField otherField)) return false;
        return otherField.getName().equals(this.getName()) &&
                otherField.getType().equals(this.getType()) &&
                otherField.getVisibility().equals(this.getVisibility());
    }

    @Override
    public String toString() {
        return "[ClassField: name=" + this.getName() + ", type=" + this.getType() + ", visibility=" + this.getVisibility() + "]";
    }
}
