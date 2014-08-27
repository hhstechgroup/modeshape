package org.modeshape.connector.cmis.operations.impl;

import java.text.MessageFormat;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.infinispan.schematic.document.Document;
import org.infinispan.schematic.document.EditableDocument;
import org.modeshape.connector.cmis.RuntimeSnapshot;
import org.modeshape.connector.cmis.config.CmisConnectorConfiguration;
import org.modeshape.connector.cmis.ObjectId;
import org.modeshape.connector.cmis.features.TempDocument;
import org.modeshape.connector.cmis.mapping.MappedCustomType;
import org.modeshape.connector.cmis.operations.BinaryContentProducerInterface;
import org.modeshape.jcr.JcrLexicon;
import org.modeshape.jcr.federation.spi.DocumentWriter;
import org.modeshape.jcr.value.Name;

import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.modeshape.jcr.ModeShapeLexicon;

/*
 * Single Version Feature supporting operations
 *
 * Does creation of document in external system with a single action
 * which takes place when jcr submits document's content to store action
 * before that action all teh incoming data are stored in temporary document
 * held by connector
 *
 * Until document is create in external system jcr uses fake GUID
 * generated by connector to access the object
 *
 * However this guid must be kept by connector/externalCmis for
 * jcr to be able to query the document
 * thus, generated/fake guid is stored in the special custom property of an object
 * Property name is defined in SingleVersionOptions settings
 *
 * Having of such a property make connector to utilize extended document search approach
 * along with standard GetObject which is not able to find object by it's Fake GUID
 * as it is not known by external system
 *
 * Additional search logic is a query conducted and executed by connector
 *
 * note : Single version is applicable to documents only, so folders may be retrieved with standard getObject call
 * note : single version logic is applied to the types defined in SingleVersionOptions block
 * note : if incoming cmis object has <commonIdProperty> set it is considered as objectId
 *        even if document's type is not listed in SingleVersionOptions. Because SingleVersionOptions
 *        might had been changed after object is created.
 *
 */
public class CmisSingleVersionOperations extends CmisOperation {

    public CmisSingleVersionOperations(RuntimeSnapshot snapshot,
                                       CmisConnectorConfiguration config) {
        super(snapshot, config);
    }

    /*
    * if requested id presented in temporary documents cache then
    * it's ought to be saved as single version
    */
    public boolean doStoreAsSingleVersion(ObjectId objectId) {
        return snapshot.getSingleVersionCache().containsKey(objectId.getIdentifier());
    }

    /*
    * create external document and drop cached local Temporary object
    * and its references
    */
    public void storeDocument(ObjectId objectId, Document document, CmisNewObjectCombinedOperation cmisStoreOperation,
                              BinaryContentProducerInterface binaryContentProducer) {
        long startTime = System.currentTimeMillis();
        debug("Start CmisSingleVersionOperations:storeDocument for objectId = ", objectId == null ? "null" : objectId.getIdentifier());
        
        TempDocument tempDocument = snapshot.getSingleVersionCache().get(objectId.getIdentifier());
        if (objectId.getType() == ObjectId.Type.CONTENT) {
            cmisStoreOperation.storeDocument(
                    tempDocument.getParentId(), tempDocument.getName(), tempDocument.getPrimaryType(),
                    tempDocument.getDocument(),
                    document, binaryContentProducer);
            snapshot.getSingleVersionCache().remove(objectId.getIdentifier());
            // seems must clean reference as well todo check
            snapshot.getSingleVersionCache().removeReference(tempDocument.getParentId(), objectId.getIdentifier());
        } else {
            tempDocument.setDocument(document);
        }
        debug("Finish CmisSingleVersionOperations:storeDocument for objectId = ", objectId.getIdentifier(), ". Time: ", Long.toString(System.currentTimeMillis()-startTime), " ms");
    }

    /*
    * tests target type to define whether it has to be saved as singleVersion
    * criteria: type must be listed in SingleVersionOptions.singleVersionTypes + must be a descendant of cmis:document
    */
    public boolean doAsSingleVersion(Name primaryType) {
        long startTime = System.currentTimeMillis();
        debug("Start CmisSingleVersionOperations:doAsSingleVersion for primaryType = ", primaryType == null ? "null" : primaryType.getLocalName());
        MappedCustomType mappedType = localTypeManager.getMappedTypes().findByJcrName(primaryType.toString());
        String cmisObjectTypeName = mappedType.getExtName();
        // need to resolve jcr name to prefixed/humanReadable
        boolean doAsSingleVersion = singleVersionOptions.getSingleVersionTypeNames().contains(primaryType);
        ObjectType typeDefinition = localTypeManager.getTypeDefinition(session, cmisObjectTypeName);

        boolean result = doAsSingleVersion && typeDefinition.getBaseTypeId() == BaseTypeId.CMIS_DOCUMENT;
        debug("Finish CmisSingleVersionOperations:doAsSingleVersion for primaryType = ", primaryType.getLocalName(), " with result = ", Boolean.toString(result), ". Time: ", Long.toString(System.currentTimeMillis()-startTime), " ms");
        return result;
    }

    /*
    * stores incoming parameters in the cache
    * returns generated fake guid to jcr
    * optional prefix (configurable) as added to a generated guid
    *
    * additionally generated guid/name added to parent's references as virtual child
    */
    public String newDocumentId(String parentId,
                                Name name,
                                Name primaryType) {
        long startTime = System.currentTimeMillis();
        debug("Start CmisSingleVersionOperations:newDocumentId for parentId = ", getPossibleNullString(parentId), " and name = ", name == null ? "null" : name.getLocalName());
        
        String newGUID = singleVersionOptions.getSingleVersionGUIDPrefix() + UUID.randomUUID().toString();
        String resultGuid = singleVersionOptions.commonIdValuePreProcess(newGUID);
        TempDocument value = new TempDocument(parentId, name, primaryType);
        snapshot.getSingleVersionCache().put(resultGuid, value);
        // parent
        List<String> childValues = snapshot.getSingleVersionCache().getReferences(parentId);
        if (childValues == null) childValues = new ArrayList<String>();
        childValues.add(resultGuid);
        snapshot.getSingleVersionCache().putReferences(parentId, childValues);
        debug("Finish CmisSingleVersionOperations:newDocumentId for parentId = ", getPossibleNullString(parentId), " and name = ", name == null ? "null" : name.getLocalName(), " with result = ", getPossibleNullString(resultGuid), ". Time:", Long.toString(System.currentTimeMillis()-startTime), "ms");
        
        return resultGuid;
    }

    /*
    * appends virtual children to an object
    * those virtual children are temp documents that not yet created in the external repository
    * but already attached to parent by jcr/modeshape
    */
    public void addCachedChildren(String id, DocumentWriter writer) {
        long startTime = System.currentTimeMillis();
        debug("Start CmisSingleVersionOperations:addCachedChildren for Id = ", getPossibleNullString(id));
        
        if (snapshot.getSingleVersionCache().containsReferences(id)) {
            List<String> strings = snapshot.getSingleVersionCache().getReferences(id);
            for (String childId : strings) {
                TempDocument tempDocument = snapshot.getSingleVersionCache().get(childId);
                if (tempDocument != null)
                    writer.addChild(childId, tempDocument.getName().getLocalName());
            }
        }
        debug("Finish CmisSingleVersionOperations:addCachedChildren for Id = ", getPossibleNullString(id), ". Time:", Long.toString(System.currentTimeMillis()-startTime), "ms");        
    }

    /*
    * return a virtual document or content requested by JCR using generated fake guid
    * these are documents that are not yet created in the external system
    *
    * todo validate that returning of empty CONTENT node does not affect performance, or else construct virtual CONTENT document
    */
    public Document getCachedTempDocument(ObjectId suggestedObjectId) {
        long startTime = System.currentTimeMillis();
        debug("Start CmisSingleVersionOperations:getCachedTempDocument for suggestedObjectId = ", suggestedObjectId == null ? "null" : suggestedObjectId.getIdentifier());
        
        TempDocument theParams = snapshot.getSingleVersionCache().get(suggestedObjectId.getIdentifier());
        if (theParams.getDocument() != null) {
            Document document = theParams.getDocument();
            log().info(MessageFormat.format("Finish CmisSingleVersionOperations:addCachedChildren for Id = {0}. Return cached document:: {1}. Time: {2} ms", suggestedObjectId.getIdentifier(), document, Long.toString(System.currentTimeMillis()-startTime)));
            return document;
        }
        if (suggestedObjectId.getType() == ObjectId.Type.OBJECT) {
            DocumentWriter writer = snapshot.getDocumentProducer().getNewDocument(suggestedObjectId.toString());
            // set correct type
            writer.setPrimaryType(theParams.getPrimaryType());
            // parents
            writer.setParents(ObjectId.toString(ObjectId.Type.OBJECT, theParams.getParentId()));
            // fill properties?
            // reference
            writer.addMixinType(NodeType.MIX_REFERENCEABLE);
            writer.addProperty(JcrLexicon.UUID, suggestedObjectId.toString());
            
            writer.addMixinType(ModeShapeLexicon.FEDERATED);
            
            // content node - mandatory child for a document
//                writer.addChild(ObjectId.toString(ObjectId.Type.CONTENT, id), JcrConstants.JCR_CONTENT);
            EditableDocument document = writer.document();
            log().info(MessageFormat.format("Finish CmisSingleVersionOperations:addCachedChildren for Id = {0}. Return cached document by init params:: {1}. Time: {2} ms", suggestedObjectId.getIdentifier(), document, Long.toString(System.currentTimeMillis()-startTime)));
            return document;
        } else if (suggestedObjectId.getType() == ObjectId.Type.CONTENT) {
            log().info(MessageFormat.format("Finish CmisSingleVersionOperations:addCachedChildren for Id = {0}. Return cached document content as NULL (SV). Time: {1} ms", suggestedObjectId.getIdentifier(), Long.toString(System.currentTimeMillis()-startTime)));
            return null;
                /*String contentId = ObjectId.toString(ObjectId.Type.CONTENT, objectId.getIdentifier());
                DocumentWriter writer = newDocument(contentId);

                writer.setPrimaryType(NodeType.NT_RESOURCE);
                writer.setParent(objectId.getIdentifier());

                // reference
                writer.addMixinType(NodeType.MIX_REFERENCEABLE);
                writer.addProperty(JcrLexicon.UUID, contentId);*/

//                Property<Object> lastModified = doc.getProperty(PropertyIds.LAST_MODIFICATION_DATE);
//                Property<Object> lastModifiedBy = doc.getProperty(PropertyIds.LAST_MODIFIED_BY);

//                writer.addProperty(JcrLexicon.LAST_MODIFIED, localTypeManager.getPropertyUtils().jcrValues(lastModified));
//                writer.addProperty(JcrLexicon.LAST_MODIFIED_BY, localTypeManager.getPropertyUtils().jcrValues(lastModifiedBy));

//                writer.addMixinType(NodeType.MIX_CREATED);
//                Property<Object> created = doc.getProperty(PropertyIds.CREATION_DATE);
//                Property<Object> createdBy = doc.getProperty(PropertyIds.CREATED_BY);
//                writer.addProperty(JcrLexicon.CREATED, localTypeManager.getPropertyUtils().jcrValues(created));
//                writer.addProperty(JcrLexicon.CREATED_BY, localTypeManager.getPropertyUtils().jcrValues(createdBy));

                /*EditableDocument document = writer.document();
                log().info("Return cached document content by init params:: " + document);
                return document;*/
        }
        log().info(MessageFormat.format("Finish CmisSingleVersionOperations:addCachedChildren for Id = {0}. Cached object is not found under given key. Throw exception. Time: {1} ms", suggestedObjectId.getIdentifier(), Long.toString(System.currentTimeMillis()-startTime)));
        throw new CmisObjectNotFoundException("Cached object is not found under given key");
    }
}
