import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;
import util.CodeSmellLogger;
import util.Index;
import util.Property;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class DataClumpDialog extends DialogWrapper {

    private List<Property> matching;
    private JRadioButton newClassButton;
    private JRadioButton existingClassButton;
    private JTextField newClassNameField;
    private JLabel newClassLabel;
    private JLabel existingClassLabel;
    private ComboBox existingComboBox;
    private HashMap<Property, JCheckBox> propertySelections = new HashMap<>();
    private TextFieldWithBrowseButton directoryBrowseButton;
    private JPanel checkBoxPanel;

    private final Project project;
    private final PsiElement current;
    private final PsiElement other;
    private final DataClumpRefactoring refactoring;


    public DataClumpDialog(DataClumpRefactoring refactoring, List<Property> matching, PsiElement current, PsiElement other) {
        super(true);
        this.project = current.getProject();
        this.matching = matching;
        this.current = current;
        this.other = other;
        this.refactoring = refactoring;
        setTitle("Data Clump Refactoring");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5); // abstände

        addRadioButtons(dialogPanel, gbc);
        addNewClassInput(dialogPanel, gbc);
        addDirectoryBrowser(dialogPanel, gbc);
        addCheckBoxPanel(dialogPanel, gbc);
        addExistingClassSelector(dialogPanel, gbc);

        configureRadioButtonActions(dialogPanel, gbc);

        return dialogPanel;
    }
    private void addRadioButtons(JPanel panel, GridBagConstraints gbc) {
        JRadioButton newClassRadioButton = new JRadioButton("Create new Class");
        JRadioButton existingClassRadioButton = new JRadioButton("Use existing Class");
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(newClassRadioButton);
        buttonGroup.add(existingClassRadioButton);
        this.newClassButton = newClassRadioButton;
        this.existingClassButton = existingClassRadioButton;

        // Standardmäßig "Enter new class name" ausgewählt
        newClassRadioButton.setSelected(true);

        // Layout der Radiobuttons
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(newClassRadioButton, gbc);
        gbc.gridx = 1;
        panel.add(existingClassRadioButton, gbc);
    }

    private void addNewClassInput(JPanel panel, GridBagConstraints gbc) {
        this.newClassLabel = new JLabel("Enter Class Name:");
        JTextField newClassField = new JTextField(20);
        this.newClassNameField = newClassField;

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        panel.add(newClassLabel, gbc);
        gbc.gridx = 1;
        panel.add(newClassField, gbc);
    }

    private void addDirectoryBrowser(JPanel panel, GridBagConstraints gbc) {
        TextFieldWithBrowseButton directoryBrowseButton = new TextFieldWithBrowseButton();
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setRoots(project.getBaseDir());

        directoryBrowseButton.addBrowseFolderListener(
                "Choose Directory",
                "Choose the target directory for the new class",
                null,
                descriptor
        );

        // Standardauswahl ist das aktuelle Verzeichnis
        directoryBrowseButton.setText(current.getContainingFile().getContainingDirectory().getVirtualFile().getPath());
        this.directoryBrowseButton = directoryBrowseButton;

        gbc.gridx = 2;
        panel.add(directoryBrowseButton, gbc);
    }

    private void addCheckBoxPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel checkBoxLabel = new JLabel("Choose which parameters or fields should be extracted into the new class:");
        JPanel checkBoxPanel = new JPanel(new FlowLayout());
        for (Property property : matching) {
            JCheckBox checkBox = new JCheckBox(property.getName() + ":" + property.getTypes());
            checkBox.setSelected(true);
            checkBox.addActionListener(e -> {
                if (existingClassButton.isSelected()) {
                    setClassSelection(refactoring.getUsableClasses(getProperties()));
                }
            });
            checkBoxPanel.add(checkBox);
            this.propertySelections.put(property, checkBox);
        }
        this.checkBoxPanel = checkBoxPanel;

        // Layout für Checkbox-Panel
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(checkBoxLabel, gbc);

        gbc.gridy = 4;
        panel.add(checkBoxPanel, gbc);
    }

    private void addExistingClassSelector(JPanel panel, GridBagConstraints gbc) {
        ComboBox<String> existingComboBox = new ComboBox<>();
        this.existingClassLabel = new JLabel("Existing class:");
        this.existingComboBox = existingComboBox;

        setClassSelection(refactoring.getUsableClasses(getProperties()));
    }

    private void configureRadioButtonActions(JPanel panel, GridBagConstraints gbc) {
        this.newClassButton.addActionListener(e -> {
            panel.remove(existingComboBox);
            panel.remove(existingClassLabel);

            gbc.gridwidth = 1;
            gbc.gridx = 0;
            gbc.gridy = 2;
            panel.add(newClassLabel, gbc);
            gbc.gridx = 1;
            panel.add(newClassNameField, gbc);
            gbc.gridx = 2;
            panel.add(directoryBrowseButton, gbc);
            panel.revalidate();
            panel.repaint();
        });

        this.existingClassButton.addActionListener(e -> {
            panel.remove(newClassLabel);
            panel.remove(newClassNameField);
            panel.remove(directoryBrowseButton);

            gbc.gridwidth = 1;
            gbc.gridx = 0;
            gbc.gridy = 2;
            panel.add(existingClassLabel, gbc);
            gbc.gridwidth = 2;
            gbc.gridx = 1;
            panel.add(existingComboBox, gbc);
            panel.revalidate();
            panel.repaint();
        });
    }

    private void setClassSelection(Set<TypeScriptClass> classes) {
        existingComboBox.removeAllItems();

        for (TypeScriptClass tsClass : classes) {
            existingComboBox.addItem(tsClass.getQualifiedName());
        }

        existingComboBox.revalidate();
        existingComboBox.repaint();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return this.newClassNameField;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {

        if (shouldCreateNewClass()) {
            // check if directory is valid
            PsiDirectory directory = getDirectory();
            if ( directory == null ) {
                return new ValidationInfo("Invalid directory Selected", this.directoryBrowseButton);
            }

            // check if entered Class Name is valid
            String className = getClassName();
            // nicht leer
            if (className == null || className.isEmpty()) {
                return new ValidationInfo("Classname cannot be empty", this.newClassNameField);
            }
            // keine sonderzeichen
            if (!className.matches("[a-zA-Z0-9_]*")) {
                return new ValidationInfo("Classname contains invalid characters", this.newClassNameField);
            }
            // make sure no class with same name already in dir
            for (PsiFile file : directory.getFiles()) {
                if (file.getName().endsWith(".ts")) {
                    List<JSClass> classes = PsiTreeUtil.getChildrenOfTypeAsList(file, JSClass.class);
                    for (JSClass psiClass : classes) {
                        if (psiClass.getName().equals(className)) {
                            return new ValidationInfo("Class " + className + " already exists in directory " + directory.getName(), newClassNameField);
                        }
                    }
                }
            }

            if (isImported(className, current.getContainingFile())) {
                return new ValidationInfo("Class " + className + " is already imported ", newClassNameField);
            }

            if (isImported(className, other.getContainingFile())) {
                return new ValidationInfo("Class " + className + " is already imported ", newClassNameField);
            }

            // TODO make sure also no variable with same name exists?

        } else {
            TypeScriptClass selectedClass = getSelectedClass();
            if (selectedClass == null) {
                return new ValidationInfo("There exists no matching class", this.existingClassButton);
            }
        }
        // check that enough properties are selected
        List<Property> selectedProperties = this.getProperties();
        if (selectedProperties.size() <= 1) {
            return new ValidationInfo("Not enough parameter or fields selected", (JComponent) this.checkBoxPanel.getComponents()[0]);
        }
        return super.doValidate();
    }

    private boolean isImported(String className, PsiFile file) {

        for (ES6ImportDeclaration importStatement : PsiTreeUtil.findChildrenOfType(file, ES6ImportDeclaration.class)) {
            if (importStatement.getNamedImports() != null && importStatement.getNamedImports().getText().contains(className)) {
                return true;
            }
        }
        return false;
    }

    public String getClassName() {
        if (this.newClassButton.isSelected()) {
            return this.newClassNameField.getText().trim();
        }
        return null;
    }

    public PsiDirectory getDirectory() {
        String path = this.directoryBrowseButton.getText();
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
        if (virtualFile != null && virtualFile.isDirectory()) {
            return PsiManager.getInstance(project).findDirectory(virtualFile);
        }
        return null;
    }

    public List<Property> getProperties() {
        List<Property> selectedProperties = new ArrayList<>();

        for (Property property : this.propertySelections.keySet()) {
            if (this.propertySelections.get(property).isSelected()) {
                selectedProperties.add(property);
            }
        }

        return selectedProperties;
    }

    public TypeScriptClass getSelectedClass() {
        if (this.existingComboBox.getComponents() == null) return null;
        String qualifiedName = (String) this.existingComboBox.getSelectedItem();
        return Index.getQualifiedNamesToClasses().get(qualifiedName);
    }

    public boolean shouldCreateNewClass() {
        return this.newClassButton.isSelected();
    }
}