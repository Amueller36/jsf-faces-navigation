package de.andre.jsfnavigation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsfAttributeHelpKnowledge {

    private static final Map<String, String> EXPLANATIONS =
            new LinkedHashMap<String, String>();

    static {
        put("value",
                "Binds the component to a model value. For input components this is normally the value submitted to the backing bean; for output components it is the value rendered to the page.");

        put("rendered",
                "Controls whether the component participates in rendering. When false, the component is not rendered and generally cannot be targeted reliably by an Ajax update until a rendered parent is refreshed.");

        put("disabled",
                "Disables user interaction with the component. Disabled input components are normally not submitted as editable values.");

        put("readonly",
                "Prevents the user from editing the value while keeping the component visible as an input-style control.");

        put("required",
                "Marks an input as mandatory during JSF validation. Validation fails when the submitted value is empty.");

        put("process",
                "PrimeFaces Ajax processing selector. Determines which components are decoded/validated/model-updated on the server for this request. Common values include @this, @form and explicit component IDs.");

        put("execute",
                "JSF/RichFaces Ajax processing selector. Determines which components participate in the server-side request lifecycle. Similar in purpose to PrimeFaces process.");

        put("update",
                "PrimeFaces Ajax render selector. Identifies components that should be re-rendered in the Ajax response after the server action completes.");

        put("render",
                "JSF/RichFaces Ajax render selector. Identifies components that are re-rendered after the Ajax request.");

        put("reRender",
                "Older RichFaces/A4J render selector. Identifies component IDs that should be re-rendered after the Ajax request.");

        put("var",
                "Creates a local variable representing the current row/item/value exposed by this component. EL expressions nested in the component can reference this variable.");

        put("itemLabel",
                "Defines the text shown to the user for a selectable item. It commonly references the local var of an autocomplete/select component.");

        put("itemValue",
                "Defines the object/value submitted for a selectable item. When it is a complex Java object, a JSF converter is often required.");

        put("converter",
                "Specifies the JSF converter used to translate between the submitted String representation and the Java model object.");

        put("completeMethod",
                "Backing-bean method called by p:autoComplete to obtain suggestions. It usually accepts the query text and returns a List/collection of matching values.");

        put("forceSelection",
                "For PrimeFaces autocomplete, restricts the value to an item selected from the suggestion list instead of accepting arbitrary free-form text.");

        put("dropdown",
                "Shows a dropdown trigger on PrimeFaces autocomplete so the user can request suggestions without typing the normal query.");

        put("widgetVar",
                "Assigns a client-side PrimeFaces widget variable. JavaScript can access the widget with PF('widgetVarName').");

        put("action",
                "Invokes a server-side action method. The return value may be used as a JSF navigation outcome.");

        put("actionListener",
                "Invokes a server-side action-listener method for the component event. Prefer action when you need navigation semantics.");

        put("listener",
                "Registers a server-side listener method for the corresponding Ajax/component event.");

        put("event",
                "Selects the client/component event that triggers this Ajax behavior, for example change, blur, rowSelect or a component-specific event.");

        put("immediate",
                "Moves processing of the action/value earlier in the JSF lifecycle. This can intentionally bypass later validation/model-update work, but is easy to misuse.");

        put("ajax",
                "Controls whether the component uses Ajax behavior rather than a full-page request where the component supports both.");

        put("global",
                "Controls whether the Ajax request participates in global PrimeFaces Ajax status handling.");

        put("async",
                "Allows the Ajax request to be sent asynchronously without being queued behind the normal PrimeFaces Ajax queue.");

        put("onstart",
                "Client-side JavaScript callback executed before the Ajax request is sent.");

        put("oncomplete",
                "Client-side JavaScript callback executed after the Ajax response has been processed.");

        put("onsuccess",
                "Client-side JavaScript callback executed when the Ajax request succeeds.");

        put("onerror",
                "Client-side JavaScript callback executed when the Ajax request fails.");

        put("for",
                "References another JSF component by client/component ID, commonly used by labels, messages and behaviors.");

        put("rowKey",
                "Provides a stable unique key for each data-table row. It is particularly important for table selection and lazy data models.");

        put("selection",
                "Binds the currently selected table/tree item or items to the backing bean.");

        put("selectionMode",
                "Controls whether selection is single, multiple, checkbox-based or another mode supported by the component.");

        put("rows",
                "Limits how many rows/items are rendered per page or request chunk, depending on the component.");

        put("paginator",
                "Enables PrimeFaces pagination controls for data components.");

        put("lazy",
                "Enables lazy data loading so only the data required for the current page/filter/sort request is fetched.");

        put("sortBy",
                "Defines the value/property used to sort rows/items.");

        put("filterBy",
                "Defines the value/property used by component filtering.");

        put("filterMatchMode",
                "Selects how filter text is compared, for example contains, startsWith or exact.");

        put("modal",
                "Makes a dialog/popup modal so interaction with the page behind it is blocked while the dialog is open.");

        put("dynamic",
                "Defers creation/loading of dialog or component content until it is requested, reducing initial page work.");

        put("cache",
                "Controls whether dynamically loaded component content/results are cached between subsequent uses.");

        put("appendTo",
                "Moves the rendered overlay/dialog element under another DOM container. This is commonly used to avoid CSS stacking/overflow problems.");

        put("styleClass",
                "Adds one or more CSS classes to the rendered component.");

        put("style",
                "Adds inline CSS declarations to the rendered component.");

        put("id",
                "JSF component identifier within its naming container. It participates in generated client IDs and Ajax component lookup.");
    }

    private JsfAttributeHelpKnowledge() {
    }

    public static String explanation(
            String attributeName) {

        return attributeName == null
                ? null
                : EXPLANATIONS.get(
                        attributeName);
    }

    public static String exampleValue(
            String attributeName,
            String type) {

        String name =
                attributeName == null
                        ? ""
                        : attributeName;

        if ("process".equals(name)
                || "execute".equals(name)) {

            return "@this";
        }

        if ("update".equals(name)
                || "render".equals(name)
                || "reRender".equals(name)
                || "for".equals(name)) {

            return "form:componentId";
        }

        if ("var".equals(name)) {
            return "item";
        }

        if ("itemLabel".equals(name)) {
            return "#{item.name}";
        }

        if ("itemValue".equals(name)) {
            return "#{item}";
        }

        if ("completeMethod".equals(name)
                || "action".equals(name)
                || "actionListener".equals(name)
                || "listener".equals(name)) {

            return "#{bean.method}";
        }

        if ("value".equals(name)
                || "selection".equals(name)
                || "sortBy".equals(name)
                || "filterBy".equals(name)
                || "rowKey".equals(name)) {

            return "#{bean.value}";
        }

        if ("widgetVar".equals(name)) {
            return "myWidget";
        }

        if ("converter".equals(name)) {
            return "myConverter";
        }

        if ("event".equals(name)) {
            return "change";
        }

        if ("styleClass".equals(name)) {
            return "my-style";
        }

        if ("style".equals(name)) {
            return "width: 100%";
        }

        if ("id".equals(name)) {
            return "componentId";
        }

        String lowerType =
                type == null
                        ? ""
                        : type.toLowerCase();

        if (lowerType.contains("boolean")) {
            return "true";
        }

        if (lowerType.contains("int")
                || lowerType.contains("long")
                || lowerType.contains("short")
                || lowerType.contains("number")) {

            return "10";
        }

        return "value";
    }

    private static void put(
            String key,
            String value) {

        EXPLANATIONS.put(
                key,
                value);
    }
}
