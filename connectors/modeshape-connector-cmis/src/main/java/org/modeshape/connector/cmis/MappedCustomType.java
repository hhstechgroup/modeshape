package org.modeshape.connector.cmis;

import java.util.*;

public class MappedCustomType {

    private String jcrNamespaceUri;
    private Set<String> ignoreExternalProperties = new HashSet<String>();
    private String jcrName;
    private String extName;
    private Map<String, String> propertyFeatures;

    Map<String, String> indexJcrProperties = new HashMap<String, String>();
    Map<String, String> indexExtProperties = new HashMap<String, String>();

    public MappedCustomType(String jcrName, String extName) {
        this.jcrName = jcrName;
        this.extName = extName;
    }

    public String getJcrName() {
        return jcrName;
    }

    public String getExtName() {
        return extName;
    }

    public String getJcrNamespaceUri() {
        return jcrNamespaceUri;
    }

    public Collection<String> getIgnoreExternalProperties() {
        return ignoreExternalProperties;
    }

    public void addPropertyMapping(String jcrName, String extName) {
        indexJcrProperties.put(jcrName, extName);
        indexExtProperties.put(extName, jcrName);
    }

    public String toJcrProperty(String cmisName) {
        String result = indexExtProperties.get(cmisName);
        return result != null ? result : cmisName;
    }

    public String toExtProperty(String jcrName) {
        String result = indexJcrProperties.get(jcrName);
        return result != null ? result : jcrName;
    }

    public void setJcrNamespaceUri(String jcrNamespaceUri) {
        this.jcrNamespaceUri = jcrNamespaceUri;
    }

    public void setIgnoreExternalProperties(Iterable<String> ignoreExternalProps) {
        for (String sType : ignoreExternalProps) {
            this.ignoreExternalProperties.add(sType.trim());
        }
    }

    public boolean hasFeature(String featureName){
        return propertyFeatures.containsKey(featureName);
    }

    public String getFeature(String featureName) {
        return propertyFeatures.get(featureName);
    }

    public Set<String> getFeaturesList(){
        return propertyFeatures.keySet();
    }
}
