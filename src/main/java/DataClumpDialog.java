
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;
import util.Property;
import javax.swing.*;
import java.awt.*;
import java.util.List;


public class DataClumpDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(DataClumpDialog.class);


    private List<Property> matching;
    private JRadioButton newClassButton;
    private JTextField newClassNameField;
    private ComboBox existingComboBox;
    private JPanel checkboxPanel;
    private Project project;
    private PsiElement current;
    private PsiElement other;


    public DataClumpDialog(List<Property> matching, PsiElement current, PsiElement other) {
        super(true);
        this.project = current.getProject();
        this.matching = matching;
        this.current = current;
        this.other = other;
        setTitle("Data Clump Refactoring");
        init();
    }

    public String getData() {
        return "Data";
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        // Hauptpanel mit GridBagLayout
        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5); // Abstände zwischen den Elementen

        // Radiobuttons für Auswahlmodus
        JRadioButton newClassRadioButton = new JRadioButton("Create new Class");
        JRadioButton existingClassRadioButton = new JRadioButton("Use existing Class");
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(newClassRadioButton);
        buttonGroup.add(existingClassRadioButton);
        this.newClassButton = newClassRadioButton;

        // Standardmäßig "Enter new class name" ausgewählt
        newClassRadioButton.setSelected(true);

        // Layout der Radiobuttons
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        dialogPanel.add(newClassRadioButton, gbc);
        gbc.gridx = 1;
        dialogPanel.add(existingClassRadioButton, gbc);


        // Panel für Klassennamen-Eingabe
        JLabel newClassLabel = new JLabel("Enter Class Name:");
        JTextField newClassField = new JTextField(20);
        this.newClassNameField = newClassField;

        // Panel für bestehende Klassen-Auswahl
        JLabel existingClassLabel = new JLabel("Existing class:");
        ComboBox<String> existingComboBox = new ComboBox<>(new String[]{"Class 1", "Class 2", "Class 3"});
        this.existingComboBox = existingComboBox;

        // Layout für Klassen-Eingabe / Auswahl
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        dialogPanel.add(newClassLabel, gbc);
        gbc.gridx = 1;
        dialogPanel.add(newClassField, gbc);


        // File Browser für Directory
        TextFieldWithBrowseButton directoryBrowseButton = new TextFieldWithBrowseButton();
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();

        directoryBrowseButton.addBrowseFolderListener(
                "Choose Directory",
                "Choose the target directory for the new class",
                null,
                descriptor
        );

        // Layout für Browser
        gbc.gridx = 2;
        dialogPanel.add(directoryBrowseButton, gbc);

        // Checkboxen für Parameter / Klassenfelder
        JLabel checkBoxLabel = new JLabel("Choose which parameters or fields should be extracted into the new class:");
        JPanel checkboxPanel = new JPanel(new FlowLayout());
        for (Property property : matching) {
            JCheckBox checkBox = new JCheckBox(property.getName() + " : " + property.getType());
            checkBox.setSelected(true);
            checkboxPanel.add(checkBox);
        }
        this.checkboxPanel = checkboxPanel;

        // Layout für Label
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;
        dialogPanel.add(checkBoxLabel, gbc);
        // Layout für Checkboxen
        gbc.gridy = 4;
        dialogPanel.add(checkboxPanel, gbc);

        // ActionListener für Radiobuttons, um Sichtbarkeit zu steuern
        newClassRadioButton.addActionListener(e -> {
            dialogPanel.remove(existingComboBox);
            dialogPanel.remove(existingClassLabel);
            // Layout
            gbc.gridwidth = 1;
            gbc.gridx = 0;
            gbc.gridy = 2;
            dialogPanel.add(newClassLabel, gbc);
            gbc.gridx = 1;
            dialogPanel.add(newClassField, gbc);
            gbc.gridx = 2;
            dialogPanel.add(directoryBrowseButton, gbc);
            dialogPanel.revalidate();
            dialogPanel.repaint();
        });

        existingClassRadioButton.addActionListener(e -> {
            dialogPanel.remove(newClassLabel);
            dialogPanel.remove(newClassField);
            dialogPanel.remove(directoryBrowseButton);
            // Layout
            gbc.gridwidth = 1;
            gbc.gridx = 0;
            gbc.gridy = 2;
            dialogPanel.add(existingClassLabel, gbc);
            gbc.gridx = 1;
            dialogPanel.add(existingComboBox, gbc);
            dialogPanel.revalidate();
            dialogPanel.repaint();
        });


        return dialogPanel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        // check if entered Class Name is valid
        if (this.newClassButton.isSelected()) {
            String className = getClassName();

            if (className == null || className.isEmpty()) {
                return new ValidationInfo("Classname cannot be empty", newClassNameField);
            }

            if (!className.matches("[a-zA-Z0-9_]*")) {
                return new ValidationInfo("Classname contains invalid characters", newClassNameField);
            }

            PsiDirectory directory = current.getContainingFile().getContainingDirectory();
            for (PsiFile file : directory.getFiles()) {
                if (file.getName().endsWith(".ts")) {
                    List<TypeScriptClass> classes = PsiTreeUtil.getChildrenOfTypeAsList(file, TypeScriptClass.class);
                    for (TypeScriptClass psiClass : classes) {
                        if (psiClass.getName().equals(className)) {
                            return new ValidationInfo("Class " + className + " already exists in directory " + directory.getName(), newClassNameField);
                        }
                    }
                }
            }

        }



        return super.doValidate();
    }

    private String getClassName() {
        if (this.newClassButton.isSelected()) {
            return this.newClassNameField.getText().trim();
        }
        return null;
    }


}