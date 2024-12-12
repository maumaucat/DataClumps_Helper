import com.intellij.ide.HelpTooltip;
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;
import util.Index;
import util.Property;
import util.PsiUtil;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog for the Data Clump Refactoring that allows the user to select which parameters or fields should be extracted
 * into a new class. The user can also choose to create a new class or use an existing one.
 */
public class DataClumpDialog extends DialogWrapper {

    /**
     * List of properties that are part of the data clump.
     */
    private final List<Property> matching;

    /**
     * The project and the two elements that are part of the data clump.
     */
    private final Project project;
    /**
     * The first element that is part of the data clump.
     */
    private final PsiElement current;
    /**
     * The second element that is part of the data clump.
     */
    private final PsiElement other;
    /**
     * The refactoring quickfix.
     */
    private final DataClumpRefactoring refactoring;

    /**
     * GUI elements for the dialog.
     */
    private JRadioButton newClassButton;
    private JRadioButton existingClassButton;
    private JTextField newClassNameField;
    private JLabel newClassLabel;
    private JLabel existingClassLabel;
    private ComboBox<String> existingComboBox;
    private final HashMap<Property, JCheckBox> propertySelections = new HashMap<>();
    private TextFieldWithBrowseButton directoryBrowseButton;
    private JPanel checkBoxPanel;

    /**
     * Creates a new DataClumpDialog.
     *
     * @param refactoring the refactoring quickfix
     * @param matching    the list of properties that are part of the data clump
     * @param current     the first element that is part of the data clump
     * @param other       the second element that is part of the data clump
     */
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

    /**
     * Creates the center panel of the dialog.
     *
     * @return the center panel of the dialog
     */
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5); // abstände

        addInfoPanel(dialogPanel, gbc);
        addRadioButtons(dialogPanel, gbc);
        addNewClassInput(dialogPanel, gbc);
        addDirectoryBrowser(dialogPanel, gbc);
        addCheckBoxPanel(dialogPanel, gbc);
        addExistingClassSelector();

        configureRadioButtonActions(dialogPanel, gbc);

        return dialogPanel;
    }


    /**
     * Adds the radio buttons to the dialog panel. The user can choose to create a new class or use an existing one.
     *
     * @param panel the dialog panel
     * @param gbc   the grid bag constraints
     */
    private void addRadioButtons(JPanel panel, GridBagConstraints gbc) {
        JRadioButton newClassRadioButton = new JRadioButton("Create new Class");
        JRadioButton existingClassRadioButton = new JRadioButton("Use existing Class");
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(newClassRadioButton);
        buttonGroup.add(existingClassRadioButton);
        this.newClassButton = newClassRadioButton;
        this.existingClassButton = existingClassRadioButton;

        // standard selection -> new class
        newClassRadioButton.setSelected(true);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(newClassRadioButton, gbc);
        gbc.gridx = 1;
        panel.add(existingClassRadioButton, gbc);
    }

    /**
     * Adds the info panel to the dialog panel. The info panel contains a description of the refactoring.
     *
     * @param dialogPanel the dialog panel
     * @param gbc         the grid bag constraints
     */
    private void addInfoPanel(JPanel dialogPanel, GridBagConstraints gbc) {
        String info = "Refactoring Data Clump between " + PsiUtil.getName(current) + " and " + PsiUtil.getName(other);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JBLabel(info));
        JLabel tip = new JBLabel(IconLoader.getIcon("/icons/contextHelp.svg", getClass()));
        HelpTooltip helpTooltip = new HelpTooltip();

        String DATACLUMP_EXPLANATION = "Data Clumps are defined by <a href='https://martinfowler.com/bliki/DataClump.html'>Fowler</a> as a group of parameters " +
                "or fields that appear together repeatedly at various places in the code, possibly in different orders." +
                " These can be a sign that the code needs to be refactored. " +
                "This refactoring allows you to extract these parameters or fields into a new class.";

        helpTooltip.setDescription(DATACLUMP_EXPLANATION);
        helpTooltip.installOn(tip);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(tip);
        dialogPanel.add(panel, gbc);
    }

    /**
     * Adds the input field for the new class name to the dialog panel.
     *
     * @param panel the dialog panel
     * @param gbc   the grid bag constraints
     */
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

    /**
     * Adds the directory browser to the dialog panel. The user can choose the target directory for the new class.
     *
     * @param panel the dialog panel
     * @param gbc   the grid bag constraints
     */
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

        // default directory is the directory of the element from which the refactoring is triggered
        directoryBrowseButton.setText(current.getContainingFile().getContainingDirectory().getVirtualFile().getPath());
        this.directoryBrowseButton = directoryBrowseButton;

        gbc.gridx = 3;
        panel.add(directoryBrowseButton, gbc);
    }

    /**
     * Adds the checkbox panel to the dialog panel. The user can select which parameters or fields should be extracted
     * into the new class.
     *
     * @param panel the dialog panel
     * @param gbc   the grid bag constraints
     */
    private void addCheckBoxPanel(JPanel panel, GridBagConstraints gbc) {
        JLabel checkBoxLabel = new JLabel("Choose which parameters or fields should be extracted into the new class:");
        JPanel checkBoxPanel = new JPanel(new FlowLayout());
        for (Property property : matching) {
            JCheckBox checkBox = new JCheckBox(property.getName() + ":" + property.getTypes());
            checkBox.setSelected(true);
            checkBox.addActionListener(e -> {
                setClassSelection(refactoring.getUsableClasses(getProperties()));
                existingClassButton.setEnabled(existingComboBox.getItemCount() != 0);
            });
            checkBoxPanel.add(checkBox);
            this.propertySelections.put(property, checkBox);
        }
        this.checkBoxPanel = checkBoxPanel;

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(checkBoxLabel, gbc);

        gbc.gridy = 4;
        panel.add(checkBoxPanel, gbc);
    }

    /**
     * Creates the existing class selector. The user can choose an existing class to which the selected parameters or
     * fields should be extracted.
     */
    private void addExistingClassSelector() {
        ComboBox<String> existingComboBox = new ComboBox<>();
        this.existingClassLabel = new JLabel("Existing class:");
        this.existingComboBox = existingComboBox;

        setClassSelection(refactoring.getUsableClasses(getProperties()));
        if (existingComboBox.getItemCount() == 0) {
            existingClassButton.setEnabled(false);
        }
    }

    /**
     * Adds listener to the radio buttons to switch between creating a new class and using an existing one.
     *
     * @param panel the dialog panel
     * @param gbc   the grid bag constraints
     */
    private void configureRadioButtonActions(JPanel panel, GridBagConstraints gbc) {
        this.newClassButton.addActionListener(e -> {
            panel.remove(existingClassLabel);
            panel.remove(existingComboBox);

            gbc.gridwidth = 1;
            gbc.gridy = 2;
            gbc.gridx = 0;
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
            gbc.gridy = 2;
            gbc.gridx = 0;
            panel.add(existingClassLabel, gbc);
            gbc.gridwidth = 2;
            gbc.gridx = 1;
            panel.add(existingComboBox, gbc);
            panel.revalidate();
            panel.repaint();
        });
    }

    /**
     * Sets the class selection in the existing class selector.
     *
     * @param classes the classes that can be selected
     */
    private void setClassSelection(Set<TypeScriptClass> classes) {
        existingComboBox.removeAllItems();

        for (TypeScriptClass tsClass : classes) {
            existingComboBox.addItem(tsClass.getQualifiedName());
        }

        existingComboBox.revalidate();
        existingComboBox.repaint();
    }

    /**
     * Returns with the preferred focused component.
     *
     * @return the preferred focused component
     */
    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return this.newClassNameField;
    }

    /**
     * Validates the user input. Checks if the entered class name is valid, if the directory is valid, if the class name
     * is not empty, if the class name does not contain special characters, if the class name does not already exist in
     * the directory, if the class name is not already imported, and if enough parameters or fields are selected.
     *
     * @return the validation info
     */
    @Override
    protected @Nullable ValidationInfo doValidate() {

        if (shouldCreateNewClass()) {
            // check if directory is valid
            PsiDirectory directory = getDirectory();
            if (directory == null) {
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
                        if (Objects.equals(psiClass.getName(), className)) {
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

    /**
     * Checks if the class name is already imported in the file.
     *
     * @param className the class name
     * @param file      the file
     * @return true if the class name is already imported in the file, false otherwise
     */
    private boolean isImported(String className, PsiFile file) {

        for (ES6ImportDeclaration importStatement : PsiTreeUtil.findChildrenOfType(file, ES6ImportDeclaration.class)) {
            if (importStatement.getNamedImports() != null && importStatement.getNamedImports().getText().contains(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the class name that the user entered.
     *
     * @return the class name that the user entered
     */
    public String getClassName() {
        if (this.newClassButton.isSelected()) {
            return this.newClassNameField.getText().trim();
        }
        return null;
    }

    /**
     * Returns the directory that the user selected.
     *
     * @return the directory that the user selected
     */
    public PsiDirectory getDirectory() {
        String path = this.directoryBrowseButton.getText();
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
        if (virtualFile != null && virtualFile.isDirectory()) {
            return PsiManager.getInstance(project).findDirectory(virtualFile);
        }
        return null;
    }

    /**
     * Returns the properties that the user selected.
     *
     * @return the properties that the user selected
     */
    public List<Property> getProperties() {
        List<Property> selectedProperties = new ArrayList<>();

        for (Property property : this.propertySelections.keySet()) {
            if (this.propertySelections.get(property).isSelected()) {
                selectedProperties.add(property);
            }
        }

        return selectedProperties;
    }

    /**
     * Returns the selected class.
     *
     * @return the selected class
     */
    public TypeScriptClass getSelectedClass() {
        if (this.existingComboBox.getComponents() == null) return null;
        String qualifiedName = (String) this.existingComboBox.getSelectedItem();
        return Index.getQualifiedNamesToClasses().get(qualifiedName);
    }

    /**
     * Returns if the user wants to create a new class.
     *
     * @return true if the user wants to create a new class, false otherwise
     */
    public boolean shouldCreateNewClass() {
        return this.newClassButton.isSelected();
    }

}