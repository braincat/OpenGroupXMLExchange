/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.opengroup.archimate.xmlexchange;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.jdom.JDOMUtils;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.IRelationship;



/**
 * Export Archi Model to Open Exchange XML Format using JDOM
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class XMLModelExporter implements IXMLExchangeGlobals {
    
    private IArchimateModel fModel;
    
    // Properties
    private Map<String, String> fPropertyDefsList;

    /**
     * A map of DC metadata element tags mapped to values
     */
    private Map<String, String> fMetadata;
    
    /**
     * Whether to save organisation of folders
     */
    private boolean fDoSaveOrganisation;
    
    /**
     * Whether to copy XSD files
     */
    private boolean fIncludeXSD;

    /**
     * The language code
     */
    private String fLanguageCode;

    public void exportModel(IArchimateModel model, File outputFile) throws IOException {
        fModel = model;
        
        // JDOM Document
        Document doc = createDocument();
        
        // Root Element
        Element rootElement = createRootElement(doc);

        // Persist model
        writeModel(rootElement);
        
        // Save
        JDOMUtils.write2XMLFile(doc, outputFile);
        
        // XSD
        if(fIncludeXSD) {
            File out = new File(outputFile.getParentFile(), XMLExchangePlugin.ARCHIMATE_XSD);
            XMLExchangePlugin.INSTANCE.copyXSDFile(XMLExchangePlugin.ARCHIMATE_XSD, out);
            
            // Dublin Core
            if(hasMetadata()) {
                out = new File(outputFile.getParentFile(), XMLExchangePlugin.DUBLINCORE_XSD);
                XMLExchangePlugin.INSTANCE.copyXSDFile(XMLExchangePlugin.DUBLINCORE_XSD, out);
            }
        }
    }
    
    /**
     * Set DC Metadata
     * @param metadata A map of DC metadata element tags mapped to values
     */
    public void setMetadata(Map<String, String> metadata) {
        fMetadata = metadata;
    }
    
    boolean hasMetadata() {
        if(fMetadata != null) {
            for(String value : fMetadata.values()) {
                if(StringUtils.isSet(value)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Set whether to save organisation of folders
     * @param set
     */
    public void setSaveOrganisation(boolean set) {
        fDoSaveOrganisation = set;
    }
    
    /**
     * Set whether to copy XSD files to target
     * @param set
     */
    public void setIncludeXSD(boolean set) {
        fIncludeXSD = set;
    }
    
    /**
     * Set the language code to use
     * @param languageCode
     */
    public void setLanguageCode(String languageCode) {
        fLanguageCode = languageCode;
    }

    /**
     * @return A JDOM Document
     */
    Document createDocument() {
        return new Document(); 
    }
    
    /**
     * @param doc
     * @return The Root JDOM Element
     */
    Element createRootElement(Document doc) {
        Element rootElement = new Element(ELEMENT_MODEL, OPEN_GROUP_NAMESPACE);
        doc.setRootElement(rootElement);

        rootElement.addNamespaceDeclaration(JDOMUtils.XSI_Namespace);
        // rootElement.addNamespaceDeclaration(OPEN_GROUP_NAMESPACE_EMBEDDED); // Don't include this
        
        // DC Namespace
        if(hasMetadata()) {
            rootElement.addNamespaceDeclaration(DC_NAMESPACE);
        }

        /* 
         * Add Schema Location Attribute which is constructed from Target Namespaces and file names of Schemas
         */
        StringBuffer schemaLocationURI = new StringBuffer();
        
        // Open Group Schema Location
        schemaLocationURI.append(rootElement.getNamespace().getURI());
        schemaLocationURI.append(" ");  //$NON-NLS-1$
        schemaLocationURI.append(OPEN_GROUP_SCHEMA_LOCATION);
        
        // DC Schema Location
        if(hasMetadata()) {
            schemaLocationURI.append(" ");  //$NON-NLS-1$
            schemaLocationURI.append(DC_NAMESPACE.getURI());
            schemaLocationURI.append(" ");  //$NON-NLS-1$
            schemaLocationURI.append(DC_SCHEMA_LOCATION);
        }
        
        rootElement.setAttribute(JDOMUtils.XSI_SchemaLocation, schemaLocationURI.toString(), JDOMUtils.XSI_Namespace);

        return rootElement;
    }
    
    /**
     * Write the model
     */
    private void writeModel(Element rootElement) {
        rootElement.setAttribute(ATTRIBUTE_IDENTIFIER, createID(fModel)); //$NON-NLS-1$
        
        // Gather all properties now
        fPropertyDefsList = getAllUniquePropertyKeysForModel();
        
        // Metadata
        writeMetadata(rootElement);
        
        // Name
        if(hasSomeText(fModel.getName())) {
            Element nameElement = new Element(ELEMENT_NAME, OPEN_GROUP_NAMESPACE);
            rootElement.addContent(nameElement);
            writeElementTextWithLanguageCode(nameElement, fModel.getName());
        }
        
        // Documentation (Purpose)
        if(hasSomeText(fModel.getPurpose())) {
            Element documentationElement = new Element(ELEMENT_DOCUMENTATION, OPEN_GROUP_NAMESPACE);
            rootElement.addContent(documentationElement);
            writeElementTextWithLanguageCode(documentationElement, fModel.getPurpose());
        }

        // Model Properties
        writeProperties(fModel, rootElement);
        
        // Model Elements
        writeModelElements(rootElement);
        
        // Relationships
        writeModelRelationships(rootElement);
        
        // Organization
        if(fDoSaveOrganisation) {
            writeOrganization(rootElement);
        }
        
        // Properties Definitions
        writeModelPropertiesDefinitions(rootElement);
        
        // Views
        writeViews(rootElement);
    }
    
    // ========================================= Metadata ======================================
    
    /**
     * Write any DC Metadata
     */
    Element writeMetadata(Element parentElement) {
        if(!hasMetadata()) {
            return null;
        }
        
        Element mdElement = new Element(ELEMENT_METADATA, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(mdElement);
        
        Element schemaElement = new Element(ELEMENT_SCHEMA, OPEN_GROUP_NAMESPACE);
        schemaElement.setText("Dublin Core");
        mdElement.addContent(schemaElement);
        
        Element schemaVersionElement = new Element(ELEMENT_SCHEMAVERSION, OPEN_GROUP_NAMESPACE);
        schemaVersionElement.setText("1.1");
        mdElement.addContent(schemaVersionElement);
        
        for(Entry<String, String> entry : fMetadata.entrySet()) {
            if(StringUtils.isSet(entry.getKey()) && StringUtils.isSet(entry.getValue())) {
                Element element = new Element(entry.getKey(), DC_NAMESPACE);
                element.setText(entry.getValue());
                mdElement.addContent(element);
            }
        }
        
        return null;
    }
    
    // ========================================= Model Elements ======================================

    /**
     * Write the elements from the layers and extensions
     */
    Element writeModelElements(Element parentElement) {
        Element elementsElement = new Element(ELEMENT_ELEMENTS, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(elementsElement);
        
        writeModelElementsFolder(fModel.getFolder(FolderType.BUSINESS), elementsElement);
        writeModelElementsFolder(fModel.getFolder(FolderType.APPLICATION), elementsElement);
        writeModelElementsFolder(fModel.getFolder(FolderType.TECHNOLOGY), elementsElement);
        writeModelElementsFolder(fModel.getFolder(FolderType.MOTIVATION), elementsElement);
        writeModelElementsFolder(fModel.getFolder(FolderType.IMPLEMENTATION_MIGRATION), elementsElement);
        writeModelElementsFolder(fModel.getFolder(FolderType.CONNECTORS), elementsElement);
        
        return elementsElement;
    }
    
    /**
     * Write the elements from an Archi folder
     */
    private void writeModelElementsFolder(IFolder folder, Element parentElement) {
        List<EObject> list = new ArrayList<EObject>();
        getElements(folder, list);
        for(EObject eObject : list) {
            if(eObject instanceof IArchimateElement) {
                writeModelElement((IArchimateElement)eObject, parentElement);
             }
        }
    }
    
    /**
     * Write an element
     */
    Element writeModelElement(IArchimateElement element, Element parentElement) { 
        Element elementElement = new Element(ELEMENT_ELEMENT, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(elementElement);
        
        // Identifier
        elementElement.setAttribute(ATTRIBUTE_IDENTIFIER, createID(element));
        
        // Type
        elementElement.setAttribute(ATTRIBUTE_TYPE, XMLTypeMapper.getArchimateComponentName(element), JDOMUtils.XSI_Namespace);
        
        // Name
        if(hasSomeText(element.getName())) {
            Element nameElement = new Element(ELEMENT_LABEL, OPEN_GROUP_NAMESPACE);
            elementElement.addContent(nameElement);
            writeElementTextWithLanguageCode(nameElement, element.getName());
        }

        // Documentation
        if(hasSomeText(element.getDocumentation())) {
            Element documentationElement = new Element(ELEMENT_DOCUMENTATION, OPEN_GROUP_NAMESPACE);
            elementElement.addContent(documentationElement);
            writeElementTextWithLanguageCode(documentationElement, element.getDocumentation());
        }
        
        // Properties
        writeProperties(element, elementElement);
        
        return elementElement;
    }

    /**
     * Return all elements in an Archi folder and its sub-folders
     */
    private void getElements(IFolder folder, List<EObject> list) {
        for(EObject object : folder.getElements()) {
            list.add(object);
        }
        
        for(IFolder f : folder.getFolders()) {
            getElements(f, list);
        }
    }
    
    // ========================================= Model Relationships ======================================

    /**
     * Write the relationships
     */
    Element writeModelRelationships(Element parentElement) {
        Element relationshipsElement = new Element(ELEMENT_RELATIONSHIPS, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(relationshipsElement);
        writeModelRelationshipsFolder(fModel.getFolder(FolderType.RELATIONS), relationshipsElement);
        return relationshipsElement;
    }
    
    /**
     * Write the relationships from an Archi folder
     */
    private void writeModelRelationshipsFolder(IFolder folder, Element parentElement) {
        List<EObject> list = new ArrayList<EObject>();
        getElements(folder, list);
        for(EObject eObject : list) {
            if(eObject instanceof IRelationship) {
                writeModelRelationship((IRelationship)eObject, parentElement);
             }
        }
    }

    /**
     * Write a relationship
     */
    Element writeModelRelationship(IRelationship relationship, Element parentElement) { 
        Element relationshipElement = new Element(ELEMENT_RELATIONSHIP, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(relationshipElement);
        
        // Identifier
        relationshipElement.setAttribute(ATTRIBUTE_IDENTIFIER, createID(relationship));
        
        // Source ID
        relationshipElement.setAttribute(ATTRIBUTE_SOURCE, createID(relationship.getSource()));
        
        // Target ID
        relationshipElement.setAttribute(ATTRIBUTE_TARGET, createID(relationship.getTarget()));

        // Type
        relationshipElement.setAttribute(ATTRIBUTE_TYPE, XMLTypeMapper.getArchimateComponentName(relationship), JDOMUtils.XSI_Namespace);

        // Name
        if(hasSomeText(relationship.getName())) {
            Element nameElement = new Element(ELEMENT_LABEL, OPEN_GROUP_NAMESPACE);
            relationshipElement.addContent(nameElement);
            writeElementTextWithLanguageCode(nameElement, relationship.getName());
        }
        
        // Documentation
        if(hasSomeText(relationship.getDocumentation())) {
            Element documentationElement = new Element(ELEMENT_DOCUMENTATION, OPEN_GROUP_NAMESPACE);
            relationshipElement.addContent(documentationElement);
            writeElementTextWithLanguageCode(documentationElement, relationship.getDocumentation());
        }
        
        // Properties
        writeProperties(relationship, relationshipElement);

        return relationshipElement;
    }
    
    // ========================================= Organization ======================================

    Element writeOrganization(Element parentElement) {
        Element organizationElement = new Element(ELEMENT_ORGANIZATION, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(organizationElement);
        
        for(IFolder folder : fModel.getFolders()) {
            writeFolder(folder, organizationElement);
        }
        
        return organizationElement;
    }
    
    Element writeFolder(IFolder folder, Element parentElement) {
        if(folder.getFolders().isEmpty() && folder.getElements().isEmpty()) {
            return null;
        }
        
        Element itemFolder = new Element(ELEMENT_ITEM, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(itemFolder);
        
        Element labelElement = new Element(ELEMENT_LABEL, OPEN_GROUP_NAMESPACE);
        itemFolder.addContent(labelElement);
        writeElementTextWithLanguageCode(labelElement, folder.getName());
        
        if(hasSomeText(folder.getDocumentation())) {
            Element documentationElement = new Element(ELEMENT_DOCUMENTATION, OPEN_GROUP_NAMESPACE);
            itemFolder.addContent(documentationElement);
            writeElementTextWithLanguageCode(documentationElement, folder.getDocumentation());
        }
        
        for(IFolder subFolder : folder.getFolders()) {
            writeFolder(subFolder, itemFolder);
        }
        
        for(EObject eObject : folder.getElements()) {
            if(eObject instanceof IIdentifier) {
                IIdentifier component = (IIdentifier)eObject;
                Element itemElement = new Element(ELEMENT_ITEM, OPEN_GROUP_NAMESPACE);
                itemFolder.addContent(itemElement);
                itemElement.setAttribute(ATTRIBUTE_IDENTIFIERREF, createID(component));
            }
        }
        
        return itemFolder;
    }
    
    // ========================================= Properties ======================================

    Element writeModelPropertiesDefinitions(Element parentElement) {
        if(fPropertyDefsList.isEmpty()) {
            return null;
        }
        
        Element propertiesDefinitionsElement = new Element(ELEMENT_PROPERTYDEFS, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(propertiesDefinitionsElement);

        for(Entry<String, String> entry : fPropertyDefsList.entrySet()) {
            Element propertyDefElement = new Element(ELEMENT_PROPERTYDEF, OPEN_GROUP_NAMESPACE);
            propertiesDefinitionsElement.addContent(propertyDefElement);
            propertyDefElement.setAttribute(ATTRIBUTE_IDENTIFIER, entry.getValue());
            propertyDefElement.setAttribute(ATTRIBUTE_NAME, entry.getKey());
            propertyDefElement.setAttribute(ATTRIBUTE_TYPE, "string"); //$NON-NLS-1$
        }
        
        return propertiesDefinitionsElement;
    }
    
    /**
     * @return All unique property types in the model
     */
    Map<String, String> getAllUniquePropertyKeysForModel() {
        Map<String, String> list = new TreeMap<String, String>();
        
        String id = "propid-"; //$NON-NLS-1$
        int idCount = 1;
        
        for(Iterator<EObject> iter = fModel.eAllContents(); iter.hasNext();) {
            EObject element = iter.next();
            if(element instanceof IProperty) {
                String name = ((IProperty)element).getKey();
                if(name != null && !list.containsKey(name)) {
                    list.put(name, id + (idCount++));
                }
            }
        }
        
        return list;
    }
    
    /**
     * Write all property values for a given element
     * @param properties
     * @param parentElement
     * @return The Element or null
     */
    Element writeProperties(IProperties properties, Element parentElement) {
        if(properties.getProperties().isEmpty()) {
            return null;
        }
        
        Element propertiesElement = new Element(ELEMENT_PROPERTIES, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(propertiesElement);
        
        for(IProperty property : properties.getProperties()) {
            String name = property.getKey();
            String value = property.getValue();
            if(hasSomeText(name)) {
                String propertyRefID = fPropertyDefsList.get(name);
                if(propertyRefID != null) {
                    Element propertyElement = new Element(ELEMENT_PROPERTY, OPEN_GROUP_NAMESPACE);
                    propertiesElement.addContent(propertyElement);
                    propertyElement.setAttribute(ATTRIBUTE_IDENTIFIERREF, propertyRefID);
                    
                    Element valueElement = new Element(ELEMENT_VALUE, OPEN_GROUP_NAMESPACE);
                    propertyElement.addContent(valueElement);
                    writeElementTextWithLanguageCode(valueElement, value);
                }
            }
        }
        
        return propertiesElement;
    }
    
    // ========================================= Views ======================================
    
    Element writeViews(Element parentElement) {
        // Do we have any views?
        EList<IDiagramModel> views = fModel.getDiagramModels();
        if(views.isEmpty()) {
            return null;
        }
        
        Element viewsElement = new Element(ELEMENT_VIEWS, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(viewsElement);
        
        for(IDiagramModel dm : views) {
            if(dm instanceof IArchimateDiagramModel) {
                writeView((IArchimateDiagramModel)dm, viewsElement);
            }
        }
        
        return viewsElement;
    }
    
    Element writeView(IArchimateDiagramModel dm, Element parentElement) {
        Element viewElement = new Element(ELEMENT_VIEW, OPEN_GROUP_NAMESPACE);
        parentElement.addContent(viewElement);

        // Identifier
        viewElement.setAttribute(ATTRIBUTE_IDENTIFIER, createID(dm));

        // Viewpoint
        String viewPointName = XMLTypeMapper.getViewpointName(dm.getViewpoint());
        if(StringUtils.isSet(viewPointName)) {
            viewElement.setAttribute(ATTRIBUTE_VIEWPOINT, viewPointName);
        }

        // Name
        if(hasSomeText(dm.getName())) {
            Element nameElement = new Element(ELEMENT_LABEL, OPEN_GROUP_NAMESPACE);
            viewElement.addContent(nameElement);
            writeElementTextWithLanguageCode(nameElement, dm.getName());
        }

        // Documentation
        if(hasSomeText(dm.getDocumentation())) {
            Element documentationElement = new Element(ELEMENT_DOCUMENTATION, OPEN_GROUP_NAMESPACE);
            viewElement.addContent(documentationElement);
            writeElementTextWithLanguageCode(documentationElement, dm.getDocumentation());
        }

        // Properties
        writeProperties(dm, viewElement);
        
        return viewElement;
    }
    

    // ========================================= Helpers ======================================

    private void writeElementTextWithLanguageCode(Element element, String text) {
        element.setText(text);
        
        if(fLanguageCode != null) {
            element.setAttribute(ATTRIBUTE_LANG, fLanguageCode, Namespace.XML_NAMESPACE);
        }
    }

    /**
     * Return true if string has at least some text
     */
    private boolean hasSomeText(String string) {
        return string != null && !string.isEmpty();
    }

    private String createID(IIdentifier identifier) {
        return "id-" + identifier.getId();
    }
}
