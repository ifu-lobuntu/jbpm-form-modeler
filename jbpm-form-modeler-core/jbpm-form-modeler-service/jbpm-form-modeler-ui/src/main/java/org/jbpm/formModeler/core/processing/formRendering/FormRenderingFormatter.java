/**
 * Copyright (C) 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.formModeler.core.processing.formRendering;

import org.jbpm.formModeler.api.util.helpers.CDIHelper;
import org.jbpm.formModeler.core.UIDGenerator;
import org.jbpm.formModeler.service.bb.mvc.taglib.formatter.Formatter;
import org.jbpm.formModeler.service.bb.commons.config.componentsFactory.Factory;
import org.jbpm.formModeler.service.bb.mvc.components.handling.MessagesComponentHandler;
import org.jbpm.formModeler.service.bb.mvc.taglib.formatter.FormatterException;
import org.jbpm.formModeler.core.config.FormManagerImpl;
import org.jbpm.formModeler.api.model.Field;
import org.jbpm.formModeler.api.model.Form;
import org.jbpm.formModeler.api.model.FormDisplayInfo;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jbpm.formModeler.api.processing.FieldHandler;
import org.jbpm.formModeler.api.processing.FormProcessor;
import org.jbpm.formModeler.api.processing.FormStatusData;
import org.jbpm.formModeler.api.processing.formRendering.FormTemplateHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;
import java.util.*;

/**
 *
 */
public class FormRenderingFormatter extends Formatter {
    private static transient org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(FormRenderingFormatter.class.getName());

    public static final String ATTR_FIELD = "_ddm_currentField";
    public static final String ATTR_NAMESPACE = "_ddm_currentNamespace";
    public static final String ATTR_VALUE = "_ddm_currentValue";
    public static final String ATTR_INPUT_VALUE = "_ddm_currentInputValue";
    public static final String ATTR_NAME = "_ddm_currentName";
    public static final String ATTR_FIELD_IS_WRONG = "_ddm_currentFieldIsWrong";
    public static final String ATTR_FORM_RENDER_MODE = "_ddm_current_renderMode";

    public static final String ATTR_VALUE_IS_DYNAMIC_OBJECT = "_ddm_valueIsObject";
    public static final String ATTR_VALUE_IS_DYNAMIC_OBJECT_ARRAY = "_ddm_valueIsObjectArray";
    public static final String ATTR_DYNAMIC_OBJECT_ID = "_ddm_currentValueIds";
    public static final String ATTR_DYNAMIC_OBJECT_ENTITY_NAME = "_ddm_currentValueEntityName";

    public static final String ATTR_FIELD_IS_DISABLED = "_ddm_fieldIsDisabled";
    public static final String ATTR_FIELD_IS_READONLY = "_ddm_fieldIsReadonly";

    public static final String FIELD_CONTAINER_STYLE = "padding-top: 3px; padding-right:3px;";

    public static final String TEMPLATE_FIELD_TOKEN = "$field";
    public static final String TEMPLATE_LABEL_TOKEN = "$label";

    private String errorsPage;
    private FormManagerImpl formManagerImpl;
    private FormProcessor defaultFormProcessor = (FormProcessor) CDIHelper.getBeanByType(FormProcessor.class);
    private FormErrorMessageBuilder formErrorMessageBuilder;
    private CustomRenderingInfo renderInfo;
    private UIDGenerator uidGenerator;
    private MessagesComponentHandler messagesComponentHandler;

    private FormTemplateHelper formTemplateHelper;

    private String[] formModes = new String[]{Form.RENDER_MODE_FORM, Form.RENDER_MODE_WYSIWYG_FORM};
    private String[] displayModes = new String[]{Form.RENDER_MODE_DISPLAY, Form.RENDER_MODE_WYSIWYG_DISPLAY};

    protected Form formToPaint;
    protected String namespace;
    protected String renderMode;
    protected Boolean isDisabled = Boolean.FALSE;
    protected Boolean isReadonly = Boolean.FALSE;
    protected Long objectIdToLoad = null;

    @Override
    public void start() throws Exception {
        super.start();
        formManagerImpl = FormManagerImpl.lookup();
    }

    public UIDGenerator getUidGenerator() {
        return uidGenerator;
    }

    public void setUidGenerator(UIDGenerator uidGenerator) {
        this.uidGenerator = uidGenerator;
    }

    public FormManagerImpl getFormManager() {
        return formManagerImpl;
    }

    public void setFormManager(FormManagerImpl formManagerImpl) {
        this.formManagerImpl = formManagerImpl;
    }

    public FormProcessor getDefaultFormProcessor() {
        return defaultFormProcessor;
    }

    public void setDefaultFormProcessor(FormProcessor defaultFormProcessor) {
        this.defaultFormProcessor = defaultFormProcessor;
    }

    public CustomRenderingInfo getRenderInfo() {
        return renderInfo;
    }

    public void setRenderInfo(CustomRenderingInfo renderInfo) {
        this.renderInfo = renderInfo;
    }

    public MessagesComponentHandler getMessagesComponentHandler() {
        return messagesComponentHandler;
    }

    public void setMessagesComponentHandler(MessagesComponentHandler messagesComponentHandler) {
        this.messagesComponentHandler = messagesComponentHandler;
    }

    public String getErrorsPage() {
        return errorsPage;
    }

    public void setErrorsPage(String errorsPage) {
        this.errorsPage = errorsPage;
    }

    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws FormatterException {

        //init formToRender
        Object formObject = getParameter("form");
        if (formObject != null) formToPaint = (Form) formObject;
        else {
            Object formIdObject = getParameter("formId");
            Long formId = Long.decode(String.valueOf(formIdObject));
            formToPaint = formManagerImpl.getFormById(formId);
        }

        renderMode = (String) getParameter("renderMode");     //Default is form
        objectIdToLoad = (Long) getParameter("editId");
        String objectToLoadClass = (String) getParameter("editClass");
        String labelMode = (String) getParameter("labelMode");           //Default is before;
        String reusingStatus = (String) getParameter("reuseStatus"); //Default is true;
        String forceLabelModeParam = (String) getParameter("forceLabelMode"); //Default is false
        String displayModeParam = (String) getParameter("displayMode");
        String subForm = (String) getParameter("isSubForm");
        String multiple = (String) getParameter("isMultiple");

        Boolean disabled = (Boolean) getParameter("isDisabled");
        Boolean readonly = (Boolean) getParameter("isReadonly");

        if (disabled != null) isDisabled = disabled;    // These params can be null, this has to be evaluated
        if (readonly != null) isReadonly = readonly;

        boolean isSubForm = subForm != null && Boolean.valueOf(subForm).booleanValue();
        boolean isMultiple = multiple != null && Boolean.valueOf(multiple).booleanValue();

        Object formValues = getParameter("formValues");
        namespace = (String) getParameter("namespace");

        if (StringUtils.isEmpty(namespace)) {
            log.warn("Empty namespace is no longer permitted. Will use a default namespace value, for backwards compatibility", new Exception());
            namespace = FormProcessor.DEFAULT_NAMESPACE;
        } else if (!Character.isJavaIdentifierStart(namespace.charAt(0))) {
            log.warn("Namespace "+namespace+" starts with an illegal character. It may cause unexpected behaviour of form under IE.");
        }



        // Default render mode is FORM
        renderMode = renderMode == null ? Form.RENDER_MODE_FORM : renderMode;

        //Default label mode depends on render mode
        labelMode = labelMode == null ? Form.LABEL_MODE_BEFORE : labelMode;
        if (Form.RENDER_MODE_DISPLAY.equals(renderMode)) {
            labelMode = Form.LABEL_MODE_HIDDEN;
        }

        boolean reuseStatus = reusingStatus == null || Boolean.valueOf(reusingStatus).booleanValue();
        boolean forceLabelMode = forceLabelModeParam != null && Boolean.valueOf(forceLabelModeParam).booleanValue();
        namespace = namespace == null ? "" : namespace;

        try {
            if (log.isDebugEnabled()) {
                log.debug("Printing form " + formToPaint.getId() + ". Mode: " + renderMode + ".");
            }
            String formLabelMode = formToPaint.getLabelMode();
            if (formLabelMode != null && !"".equals(formLabelMode) && !Form.LABEL_MODE_UNDEFINED.equals(formLabelMode)) {
                if (!forceLabelMode)
                    labelMode = formLabelMode;
            }

            String formMode;

            if (Form.RENDER_MODE_FORM.equals(renderMode) || Form.RENDER_MODE_WYSIWYG_FORM.equals(renderMode)) {
                formMode = formValues == null && objectIdToLoad == null ? "create" : "edit";
            } else formMode = renderMode;

            if (formValues == null) formValues = new HashMap();

            ((Map) formValues).put(FormProcessor.FORM_MODE, formMode);

            FormStatusData formStatusData = defaultFormProcessor.read(formToPaint, namespace, (Map) formValues);
            if (!reuseStatus) {
                defaultFormProcessor.clear(formToPaint.getId(), namespace);
            }
            if (!reuseStatus || formStatusData.isNew()) {
                defaultFormProcessor.load(formToPaint.getId(), namespace, formValues, renderMode);
                /*
                TODO: review this to load data directly from the object
                if (objectToLoadClass != null && objectIdToLoad != null) {
                    if (log.isDebugEnabled())
                        log.debug("Loading into memory status object with class " + objectToLoadClass + " and id=" + objectIdToLoad + " for form " + formToPaint.getId() + " in namespace " + namespace);
                    formProcessor.load(formToPaint.getId(), namespace, objectIdToLoad, objectToLoadClass, renderMode);
                } else {
                    formProcessor.load(formToPaint.getId(), namespace, formValues, renderMode);
                } */
            } else {
                if (formValues != null) {
                    if (log.isDebugEnabled())
                        log.debug("Loading map of values into form " + formToPaint.getId() + " in namespace " + namespace + ": " + formValues);
                    defaultFormProcessor.load(formToPaint.getId(), namespace, formValues, renderMode);
                }
            }


            String displayMode = formToPaint.getDisplayMode();
            if (displayModeParam != null)
                displayMode = displayModeParam;
            FormDisplayInfo displayInfo = null;
            if (displayMode != null) {
                for (Iterator it = formToPaint.getFormDisplayInfos().iterator(); it.hasNext();) {
                    FormDisplayInfo info = (FormDisplayInfo) it.next();
                    if (info.getDisplayMode().equals(displayMode)) {
                        displayInfo = info;
                        break;
                    }
                }
            }

            if (log.isDebugEnabled())
                log.debug("About to display form " + formToPaint.getId() + " in namespace " + namespace + " with status " + defaultFormProcessor.read(formToPaint, namespace));
            display(formToPaint, namespace, displayMode, displayInfo, renderMode, labelMode, isSubForm, isMultiple);

        } catch (Exception e) {
            log.error("Error:", e);
            throw new FormatterException("Error", e);
        }
    }

    protected void setFormFieldErrors(String namespace, Form form) {
        if (namespace != null && form != null) {
            try {
                messagesComponentHandler.getErrorsToDisplay().addAll(formErrorMessageBuilder.getWrongFormErrors(namespace, form));
            } catch (Exception e) {
                log.error("Error getting error messages for object " + form.getId() + ": ", e);
            }
        }
    }

    protected void display(Form form, String namespace, String displayMode, FormDisplayInfo displayInfo, String renderMode, String labelMode, boolean isSubForm, boolean isMultiple) {

        if (!isSubForm || (isSubForm && isMultiple)) {
            setFormFieldErrors(namespace, form);
            includePage(errorsPage);
        }

        setRenderingInfoValues(form, namespace, renderMode, labelMode, displayMode);

        if (displayMode == null) {
            defaultDisplay(form, namespace, renderMode, labelMode, Form.DISPLAY_MODE_DEFAULT);
        } else {
            if (Form.DISPLAY_MODE_DEFAULT.equals(displayMode)) {
                defaultDisplay(form, namespace, renderMode, labelMode, Form.DISPLAY_MODE_DEFAULT);
            } else if (Form.DISPLAY_MODE_ALIGNED.equals(displayMode)) {
                defaultDisplay(form, namespace, renderMode, labelMode, Form.DISPLAY_MODE_ALIGNED);
            } else if (Form.DISPLAY_MODE_NONE.equals(displayMode)) {
                defaultDisplay(form, namespace, renderMode, labelMode, Form.DISPLAY_MODE_NONE);
            } else if (Form.DISPLAY_MODE_TEMPLATE.equals(displayMode)) {
                templateDisplay(form, namespace, renderMode);
            } else {
                log.error("Unsupported display mode.");
            }
        }
    }

    private CustomRenderingInfo previousRenderingInfo;

    protected void setRenderingInfoValues(Form form, String namespace, String renderMode, String labelMode, String displayMode) {
        try {
            previousRenderingInfo = (CustomRenderingInfo) renderInfo.clone();
        } catch (CloneNotSupportedException e) {
            log.error("Error: ", e);
        }
        renderInfo.setForm(form);
        renderInfo.setNamespace(namespace);
        renderInfo.setDisplayMode(displayMode);
        renderInfo.setLabelMode(labelMode);
        renderInfo.setRenderMode(renderMode);
    }

    public void afterRendering(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws FormatterException {
        super.afterRendering(httpServletRequest, httpServletResponse);
        //Restore previous status
        if (renderInfo != null)
            renderInfo.copyFrom(previousRenderingInfo);
        //If form was used just to show something, clear the form status.
        if (Form.RENDER_MODE_DISPLAY.equals(renderMode) || Form.RENDER_MODE_TEMPLATE_EDIT.equals(renderMode)) {
            if (formToPaint != null) {
                defaultFormProcessor.clear(formToPaint.getId(), namespace);
            }
        }
    }

    protected void templateDisplay(final Form form, final String namespace, final String renderMode) {
        List renderingInstructions = formTemplateHelper.getRenderingInstructions(form.getFormTemplate());
        FormRenderer renderer = new FormRenderer() {
            public void writeToOut(String text) {
                FormRenderingFormatter.this.writeToOut(text);
            }

            public void renderField(String fieldName) {
                Field field = form.getField(fieldName);
                if (field != null) {
                    setAttribute("field", field.getForm().getFormFields().iterator().next());
                    FormRenderingFormatter.this.renderFragment("beforeFieldInTemplateMode");
                    FormRenderingFormatter.this.renderField(
                            (Field) field,
                            namespace,
                            renderMode
                    );
                    FormRenderingFormatter.this.renderFragment("afterFieldInTemplateMode");
                } else {
                    setAttribute("fieldName", fieldName);
                    FormRenderingFormatter.this.renderFragment("beforeFieldInTemplateMode");
                    FormRenderingFormatter.this.writeToOut(Form.TEMPLATE_FIELD + "{" + fieldName + "}");
                    FormRenderingFormatter.this.renderFragment("afterFieldInTemplateMode");
                }
            }

            public void renderLabel(String fieldName) {
                Field field = form.getField(fieldName);
                if (field != null) {
                    FormRenderingFormatter.this.renderFragment("beforeLabelInTemplateMode");
                    FormRenderingFormatter.this.renderLabel(
                            (Field) field.getForm().getFormFields().iterator().next(),
                            namespace,
                            renderMode
                    );
                    FormRenderingFormatter.this.renderFragment("afterLabelInTemplateMode");
                } else {
                    FormRenderingFormatter.this.renderFragment("beforeLabelInTemplateMode");
                    FormRenderingFormatter.this.writeToOut(Form.TEMPLATE_LABEL + "{" + fieldName + "}");
                    FormRenderingFormatter.this.renderFragment("afterLabelInTemplateMode");
                }
            }
        };
        for (int i = 0; i < renderingInstructions.size(); i++) {
            TemplateRenderingInstruction instruction = (TemplateRenderingInstruction) renderingInstructions.get(i);
            instruction.doRender(renderer);
        }
        displayFooter(form);
    }

    protected void renderField(Field field, String namespace, String renderMode) {
        beforeRenderField(field, namespace, renderMode);
        String genericURL = (String) getParameter("URL for *");
        String fieldURL = (String) getParameter("URL for " + field.getFieldName());
        String genericOnClick = (String) getParameter("onclick for *");
        String fieldOnClick = (String) getParameter("onclick for " + field.getFieldName());
        String externalUrlForField = genericURL;
        if (!StringUtils.isEmpty(fieldURL)) {
            externalUrlForField = fieldURL;
        }
        String externalOnclickForField = genericOnClick;
        if (!StringUtils.isEmpty(fieldOnClick)) {
            externalOnclickForField = fieldOnClick;
        }

        Form form = (Form) field.getForm();
        FormStatusData fsd = defaultFormProcessor.read(form, namespace);
        boolean fieldHasErrors = fsd.getWrongFields().contains(field.getFieldName());
        String renderPage = "";
        FieldHandler fieldHandler = (FieldHandler) Factory.lookup(field.getFieldType().getManagerClass());

        if (Arrays.asList(formModes).contains(renderMode)) {
            renderPage = fieldHandler.getPageToIncludeForRendering();
        } else if (Arrays.asList(displayModes).contains(renderMode)) {
            renderPage = fieldHandler.getPageToIncludeForDisplaying();
        } else if (Form.RENDER_MODE_SEARCH.equals(renderMode)) {
            renderPage = fieldHandler.getPageToIncludeForSearching();
        }
        if (!"".equals(renderPage)) {
            boolean mustRenderExternalUrl = !StringUtils.isEmpty(externalUrlForField) && Form.RENDER_MODE_DISPLAY.equals(renderMode);
            Boolean fieldIsRequired = field.getFieldRequired();
            boolean fieldRequired = fieldIsRequired != null && fieldIsRequired.booleanValue();
            if (fieldHasErrors) renderFragment("beforeWrongField");
            if (fieldRequired) renderFragment("beforeRequiredField");
            if (mustRenderExternalUrl) {
                writeToOut(" <a href=\"" + externalUrlForField + "\"");
                if (!StringUtils.isEmpty(externalOnclickForField)) {
                    writeToOut(" onclick=\"" + externalOnclickForField + "\" ");
                }
                writeToOut(" >");
            }
            Object value = fsd.getCurrentValue(field.getFieldName());
            boolean isStringType = String.class.getName().equals(field.getFieldType().getFieldClass());
            if (value == null && isStringType) {
                Map currentInputValues = fsd.getCurrentInputValues();
                String[] values = null;
                if (currentInputValues != null) {
                    values = ((String[]) currentInputValues.get(namespace + FormProcessor.NAMESPACE_SEPARATOR + field.getForm().getId() + FormProcessor.NAMESPACE_SEPARATOR + field.getFieldName()));
                }
                value = values != null && values.length > 0 ? values[0] : value;
            }

            setRenderingAttributes(field, namespace, value, fsd, fieldHasErrors);
            // If disabled and/or readonly parameters were received from a subformformatter, pass them on to the included
            // fields (only relevant when they're set to true)
            if (isDisabled) setAttribute(ATTR_FIELD_IS_DISABLED, isDisabled);
            if (isReadonly) setAttribute(ATTR_FIELD_IS_READONLY, isReadonly);
            includePage(renderPage);
            if (mustRenderExternalUrl) {
                writeToOut("</a>");
            }
            if (fieldRequired) renderFragment("afterRequiredField");
            if (fieldHasErrors) renderFragment("afterWrongField");
        } else {
            if (Form.RENDER_MODE_TEMPLATE_EDIT.equals(renderMode)) {
                writeToOut(Form.TEMPLATE_FIELD + "{" + field.getFieldName() + "}");
            } else
                log.warn("Invalid render mode " + renderMode);
        }
        afterRenderField(field, namespace, renderMode);
    }

    protected void beforeRenderField(Field field, String namespace, String renderMode) {
        String uid = getFormManager().getUniqueIdentifier(field.getForm(), namespace, field, field.getFieldName());
        String fieldTypeCss = field.getFieldType().getCssStyle();
        String fieldCss = field.getCssStyle();
        Object overridenValue = defaultFormProcessor.getAttribute(field.getForm(), namespace, field.getFieldName() + ".cssStyle");
        String css = fieldTypeCss;
        if (!StringUtils.isEmpty(fieldCss)) {
            css = fieldCss;
        }
        if (overridenValue != null) {
            css = (String) overridenValue;
        }
        css = StringUtils.defaultString(css);
        css = StringUtils.remove(css, ' ');
        String styleToWrite = FIELD_CONTAINER_STYLE;
        StringTokenizer strtk = new StringTokenizer(css, ";");
        while (strtk.hasMoreTokens()) {
            String tk = strtk.nextToken();
            if ("display:none".equals(tk)) {
                styleToWrite = tk;
                break;
            }
        }
        writeToOut("<div style=\"" + styleToWrite + "\" id=\"" + uid + "_container\">");
    }

    protected void afterRenderField(Field field, String namespace, String renderMode) {
        writeToOut("</div>");
    }

    protected void renderLabel(Field field, String namespace, String renderMode) {
        beforeRenderLabel(field, namespace, renderMode);
        String inputId = field.getFieldType().getUniqueIdentifier(
                getUidGenerator().getUniqueIdentifiersPreffix(),
                namespace,
                field.getForm(),
                field,
                field.getFieldName()
        );
        String labelCssStyle = null;
        String labelCssClass = null;
        try {
            labelCssStyle = field.getLabelCSSStyle();
            labelCssClass = field.getLabelCSSClass();
            //Check if label style was overriden by formulas.
            Object style = defaultFormProcessor.getAttribute(field.getForm(), namespace, field.getFieldName() + ".labelCSSStyle");
            if (style != null)
                labelCssStyle = style.toString();

        } catch (Exception e) {
            log.error("Error: ", e);
        }

        if (Form.RENDER_MODE_TEMPLATE_EDIT.equals(renderMode)) {
            writeToOut(Form.TEMPLATE_LABEL + "{" + field.getFieldName() + "}");
        } else {
            Form form = field.getForm();
            FormStatusData fsd = defaultFormProcessor.read(form, namespace);
            boolean fieldHasErrors = fsd.getWrongFields().contains(field.getFieldName());
            String label = (String) getLocaleManager().localize(field.getLabel());
            Boolean fieldIsRequired = field.getFieldRequired();
            boolean fieldRequired = fieldIsRequired != null && fieldIsRequired.booleanValue() && !Form.RENDER_MODE_DISPLAY.equals(fieldIsRequired);

            String labelValue = StringEscapeUtils.escapeHtml(StringUtils.defaultString(label));

            writeToOut("<span id=\"" + inputId + "_label\"");
            writeToOut(" class='dynInputStyle " + StringUtils.defaultString(labelCssClass) + "' ");
            if (labelCssStyle != null) writeToOut(" style='" + labelCssStyle + "' ");
            writeToOut(" >");

            if (fieldHasErrors) writeToOut("<span class=\"skn-error\">");
            if (!StringUtils.isEmpty(inputId) && !StringUtils.isEmpty(labelValue) && !Form.RENDER_MODE_DISPLAY.equals(renderMode))
                writeToOut("<label for=\"" + StringEscapeUtils.escapeHtml(inputId) + "\">");
            if (fieldRequired) writeToOut("*");
            writeToOut(labelValue);
            if (!StringUtils.isEmpty(inputId) && !StringUtils.isEmpty(labelValue) && !Form.RENDER_MODE_DISPLAY.equals(renderMode))
                writeToOut("</label>");
            if (fieldRequired) renderFragment("afterRequiredLabel");
            if (fieldHasErrors) writeToOut("</span>");

            writeToOut("</span>");

        }
        afterRenderLabel(field, namespace, renderMode);
    }

    protected void beforeRenderLabel(Field field, String namespace, String renderMode) {
        String uid = getFormManager().getUniqueIdentifier(field.getForm(), namespace, field, field.getFieldName());
        String fieldCss = field.getLabelCSSStyle();
        Object overridenValue = defaultFormProcessor.getAttribute(field.getForm(), namespace, field.getFieldName() + ".labelCSSStyle");
        String css = fieldCss;
        if (overridenValue != null) {
            css = (String) overridenValue;
        }
        css = StringUtils.defaultString(css);
        css = StringUtils.remove(css, ' ');
        String styleToWrite = FIELD_CONTAINER_STYLE;
        StringTokenizer strtk = new StringTokenizer(css, ";");
        while (strtk.hasMoreTokens()) {
            String tk = strtk.nextToken();
            if ("display:none".equals(tk)) {
                styleToWrite = tk;
                break;
            }
        }
        writeToOut("<div style=\"" + styleToWrite + "\" id=\"" + uid + "_label_container\">");
    }

    protected void afterRenderLabel(Field field, String namespace, String renderMode) {
        writeToOut("</div>");
    }

    /**
     * Default display. One field after each other
     *
     * @param form
     * @param renderMode
     */
    protected void defaultDisplay(Form form, String namespace, String renderMode, String labelMode, String mode) {
        Set<Field> fields = form.getFormFields();
        List<Field> sortedFields = new ArrayList(fields);
        Collections.sort(sortedFields, new Field.Comparator());
        FormStatusData formStatusData = defaultFormProcessor.read(form, namespace);

        setAttribute("width", deduceWidthForForm(form, renderMode, labelMode, mode));
        renderFragment("outputStart");
        renderFragment("formHeader");
        /*Calculate colspans*/
        List colspans = new ArrayList();
        List fieldGroups = new ArrayList();
        fieldGroups.add(new ArrayList());
        for (Field field : sortedFields) {
            List currentList = (List) fieldGroups.get(fieldGroups.size() - 1);
            if (!Boolean.TRUE.equals(field.getGroupWithPrevious())) {
                fieldGroups.add(currentList = new ArrayList());
            }
            currentList.add(field);
        }
        for (int i = 0; i < fieldGroups.size(); i++) {
            List list = (List) fieldGroups.get(i);
            if (!list.isEmpty())
                colspans.add(new BigInteger(String.valueOf(list.size())));
        }

        BigInteger mcm = calculateMCM(colspans);
        BigInteger max = calculateMax(colspans);

        /*Render fields with colspans*/
        List groupList = new ArrayList();

        boolean first = true;

        for (int i = 0; i < sortedFields.size(); i++) {
            Field field = sortedFields.get(i);
            groupList.add(field);
            if (i < sortedFields.size() - 1) {
                Field nextField = sortedFields.get(i + 1);
                if (nextField.getGroupWithPrevious() != null && nextField.getGroupWithPrevious().booleanValue()) {
                    continue;
                }
            }
            if (i > 0 && Form.DISPLAY_MODE_NONE.equals(mode)) {
                renderFragment("outputEnd");
                setAttribute("width", deduceWidthForForm(form, renderMode, labelMode, mode));
                renderFragment("outputStart");
            }
            defaultDisplayGroup(form, groupList, mcm.intValue(), max.intValue(), renderMode, labelMode, formStatusData, mode, namespace, i, first);
            groupList.clear();
            first = false;
        }

        displayFooter(form);
        renderFragment("outputEnd");
    }


    protected void displayFooter(Form form) {
        String displayMode = form.getDisplayMode();
        if (Form.RENDER_MODE_FORM.equals(renderMode) || Form.RENDER_MODE_SEARCH.equals(renderMode)) {
            String formRefresherFieldName = namespace + FormProcessor.NAMESPACE_SEPARATOR + form.getId() + FormProcessor.NAMESPACE_SEPARATOR + ":initialFormRefresher";
            setAttribute("name", formRefresherFieldName);
            setAttribute("uid", getUidGenerator().getUniqueIdentifiersPreffix() + FormProcessor.NAMESPACE_SEPARATOR + formRefresherFieldName);
            if (Form.DISPLAY_MODE_TEMPLATE.equals(displayMode)) {
                includePage("/formModeler/defaultFormFooter.jsp");
            } else {
                renderFragment("formFooter");
            }
        }
    }

    /**
     * Deduce width for a form.
     *
     * @param form
     * @param renderMode
     * @param labelMode
     * @param mode
     * @return Deduced width for a form.
     */
    protected String deduceWidthForForm(Form form, String renderMode, String labelMode, String mode) {
        if (Form.DISPLAY_MODE_TEMPLATE.equals(mode))
            return null;  //In these modes, it doesn't matter
        if (Form.RENDER_MODE_DISPLAY.equals(renderMode)) { //Showing data
            if (Form.DISPLAY_MODE_NONE.equals(mode)) {
                return "";
            } else {
                return "100%";
            }
        } else { //Entering data
            return "1%";
        }
    }

    protected void defaultDisplayGroup(Form form, List groupMembers, int maxCols, int maxMembers, String renderMode, String labelMode, FormStatusData formStatusData, String mode, String namespace, int position, boolean first) {
        int fieldColspan = maxCols / groupMembers.size();
        int fieldWidth = (100 * fieldColspan) / maxCols;
        if (Form.DISPLAY_MODE_ALIGNED.equals(mode)) {
            fieldColspan = maxCols / maxMembers;
        }
        if (Form.DISPLAY_MODE_NONE.equals(mode)) {
            fieldColspan = 1;
        }
        setAttribute("groupPosition", position);
        setAttribute("field", groupMembers.get(0));
        setAttribute("colspan", maxCols);
        setAttribute("isFirst", first);
        renderFragment("groupStart");
        for (int i = 0; i < groupMembers.size(); i++) {
            if (i == groupMembers.size() - 1 && Form.DISPLAY_MODE_ALIGNED.equals(mode)) {
                fieldColspan = maxCols - i * maxCols / maxMembers;
                fieldWidth = (100 * fieldColspan) / maxCols;
            }
            renderInputElement((Field) groupMembers.get(i), fieldColspan, fieldWidth, namespace, renderMode, labelMode, i);
        }
        setAttribute("field", groupMembers.get(groupMembers.size()-1));
        setAttribute("colspan", maxCols);
        renderFragment("groupEnd");
    }

    protected void renderInputElement(Field field, int fieldColspan, int fieldWidth, String namespace, String renderMode, String labelMode, int index) {
        setAttribute("field", field);
        setAttribute("colspan", fieldColspan);
        setAttribute("width", fieldWidth);
        setAttribute("index", index);
        boolean labelInSameLine = Form.LABEL_MODE_LEFT.equals(labelMode) || Form.LABEL_MODE_RIGHT.equals(labelMode);
        renderFragment("beforeInputElement");

        if (Form.LABEL_MODE_BEFORE.equals(labelMode) || Form.LABEL_MODE_LEFT.equals(labelMode)) {
            setAttribute("colspan", fieldColspan);
            setAttribute("width", fieldWidth);
            setAttribute("renderHolderColor", formToPaint.getBindingColor(field));
            renderFragment("beforeLabel");

            renderLabel(field, namespace, renderMode);
            renderFragment("afterLabel");
            if (!labelInSameLine)
                renderFragment("lineBetweenLabelAndField");
        }
        setAttribute("field", field);
        setAttribute("colspan", fieldColspan);
        setAttribute("width", fieldWidth);
        renderFragment("beforeField");
        renderField(field, namespace, renderMode);
        setAttribute("field", field);
        renderFragment("afterField");

        if (Form.LABEL_MODE_AFTER.equals(labelMode) || Form.LABEL_MODE_RIGHT.equals(labelMode)) {
            if (!labelInSameLine)
                renderFragment("lineBetweenLabelAndField");
            setAttribute("colspan", fieldColspan);
            setAttribute("width", fieldWidth);
            setAttribute("renderHolderColor", formToPaint.getBindingColor(field));
            renderFragment("beforeLabel");
            renderLabel(field, namespace, renderMode);
            renderFragment("afterLabel");
        }
        setAttribute("field", field);
        renderFragment("afterInputElement");
    }

    protected BigInteger calculateMCM(List colspans) {
        if (colspans == null || colspans.isEmpty()) {
            return new BigInteger("1");
        } else if (colspans.size() == 1) {
            return (BigInteger) colspans.get(0);
        } else if (colspans.size() == 2) {
            BigInteger b1 = (BigInteger) colspans.get(0);
            BigInteger b2 = (BigInteger) colspans.get(1);
            return b1.multiply(b2).divide(b1.gcd(b2));
        } else { //Size > 2
            int halfLength = colspans.size() / 2;
            List firstHalf = colspans.subList(0, halfLength);
            List secondHalf = colspans.subList(halfLength, colspans.size());
            BigInteger b1 = calculateMCM(firstHalf);
            BigInteger b2 = calculateMCM(secondHalf);
            return b1.multiply(b2).divide(b1.gcd(b2));
        }
    }

    protected BigInteger calculateMax(List colspans) {
        BigInteger max = new BigInteger("0");
        for (int i = 0; i < colspans.size(); i++) {
            BigInteger number = (BigInteger) colspans.get(i);
            max = max.compareTo(number) < 0 ? number : max;
        }
        return max;
    }

    protected void setRenderingAttributes(Field field, String namespace, Object value, FormStatusData formStatusData, boolean isWrongField) {
        String fieldName = namespace + FormProcessor.NAMESPACE_SEPARATOR + field.getForm().getId() + FormProcessor.NAMESPACE_SEPARATOR + field.getFieldName();
        setAttribute(ATTR_FIELD, field);
        setAttribute(ATTR_VALUE, value);
        setAttribute(ATTR_INPUT_VALUE, formStatusData.getCurrentInputValue(fieldName));
        setAttribute(ATTR_FIELD_IS_WRONG, isWrongField);
        setAttribute(ATTR_NAMESPACE, namespace);
        setAttribute(ATTR_NAME, fieldName);
        setAttribute(ATTR_VALUE_IS_DYNAMIC_OBJECT, false);
        setAttribute(ATTR_VALUE_IS_DYNAMIC_OBJECT_ARRAY, false);
        setAttribute(ATTR_DYNAMIC_OBJECT_ID, null);
        setAttribute(ATTR_DYNAMIC_OBJECT_ENTITY_NAME, null);
        setAttribute(ATTR_FORM_RENDER_MODE, renderMode);
    }

    public FormErrorMessageBuilder getFormErrorMessageBuilder() {
        return formErrorMessageBuilder;
    }

    public void setFormErrorMessageBuilder(FormErrorMessageBuilder formErrorMessageBuilder) {
        this.formErrorMessageBuilder = formErrorMessageBuilder;
    }

    public FormTemplateHelper getFormTemplateHelper() {
        return formTemplateHelper;
    }

    public void setFormTemplateHelper(FormTemplateHelper formTemplateHelper) {
        this.formTemplateHelper = formTemplateHelper;
    }
}