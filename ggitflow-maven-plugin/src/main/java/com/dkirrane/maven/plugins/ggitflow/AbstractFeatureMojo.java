/*
 * Copyright 2014 Desmond Kirrane.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dkirrane.maven.plugins.ggitflow;

import static com.dkirrane.gitflow.groovy.Constants.DEFAULT_FEATURE_BRN_PREFIX;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.jfrog.hudson.util.GenericArtifactVersion;

/**
 *
 */
public class AbstractFeatureMojo extends AbstractGitflowMojo {

    /**
     * The name for the feature branch
     *
     * @since 1.2
     */
    @Parameter(property = "featureName", required = false)
    protected String featureName;

    /**
     * If <code>true</code>, the feature branch name is added to the pom
     * versions
     *
     * @since 1.2
     */
    @Parameter(property = "enableFeatureVersions", defaultValue = "true", required = false)
    protected boolean enableFeatureVersions;

    public String getFeatureBranchPrefix() {
        String prefix = getGitflowInit().getFeatureBranchPrefix();
        if (StringUtils.isBlank(prefix)) {
            prefix = DEFAULT_FEATURE_BRN_PREFIX;
        }
        return prefix;
    }

    public String trimFeatureName(String name) throws MojoFailureException {
        if (StringUtils.isBlank(name)) {
            throw new MojoFailureException("Missing argument <name>");
        }

        // remove whitespace
        name = name.replaceAll("\\s+", "");

        // trim off starting any leading 'feature/' prefix
        String prefix = getFeatureBranchPrefix();
        if (name.startsWith(prefix)) {
            name = name.substring(prefix.length());
        }

        return name;
    }

    public String getFeatureVersion(String currentVersion, String featureLabel) throws MojoFailureException {
        getLog().debug("getFeatureVersion from '" + currentVersion + "'");
        featureLabel = getValidFeatureVersionAnnotation(featureLabel);
        getLog().debug("Feature version annotation '" + featureLabel + "'");

        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(currentVersion);
        String primaryNumbersAsString = artifactVersion.getPrimaryNumbersAsString();
        String annotationAsString = artifactVersion.getAnnotationAsString();
        Character annotationSeparator = artifactVersion.getAnnotationRevisionSeparator();
        String buildSpecifier = artifactVersion.getBuildSpecifier();
        Character buildSpecifierSeparator = artifactVersion.getBuildSpecifierSeparator();

        getLog().debug("Parsed version = " + artifactVersion.toString());
        if (!StringUtils.isBlank(annotationAsString)) {
            throw new MojoFailureException("Cannot add feature name to version. An annotation " + annotationAsString + " already exists");
        }

        final StringBuilder result = new StringBuilder(30);

        result.append(primaryNumbersAsString).append(annotationSeparator).append(featureLabel);

        if (buildSpecifier != null) {
            if (buildSpecifierSeparator != null) {
                result.append(buildSpecifierSeparator);
            }
            result.append(buildSpecifier);
        }

        return result.toString();
    }

    private String getValidFeatureVersionAnnotation(String featureLabel) {
        featureLabel = featureLabel.trim().replaceAll("\\s+", "_");
        featureLabel = featureLabel.trim().replaceAll("-+", "_");
        return featureLabel;
    }
}
