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

import com.dkirrane.gitflow.groovy.GitflowFeature;
import com.dkirrane.gitflow.groovy.ex.GitflowException;
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 *
 */
@Mojo(name = "feature-finish", aggregator = true)
public class FeatureFinishMojo extends AbstractFeatureMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        getLog().info("Finishing feature '" + featureName + "'");

        if (StringUtils.isBlank(featureName)) {
            String currentBranch = getGitflowInit().gitCurrentBranch();
            if (currentBranch.startsWith(getFeatureBranchPrefix())) {
                featureName = currentBranch;
            }
        } else {
            featureName = getFeatureName(featureName);
        }

        List<String> featureBranches = getGitflowInit().gitLocalFeatureBranches();

        featureName = promptForExistingFeatureName(featureBranches, featureName);

        String version = project.getVersion();
        String nonFeatureVersion = getNonFeatureVersion(version, featureName);

        getLog().info("START org.codehaus.mojo:versions-maven-plugin:2.1:set '" + nonFeatureVersion + "'");
        executeMojo(
                plugin(
                        groupId("org.codehaus.mojo"),
                        artifactId("versions-maven-plugin"),
                        version("2.1")
                ),
                goal("set"),
                configuration(
                        element(name("generateBackupPoms"), "false"),
                        element(name("newVersion"), nonFeatureVersion)
                ),
                executionEnvironment(
                        project,
                        session,
                        pluginManager
                )
        );
        getLog().info("DONE org.codehaus.mojo:versions-maven-plugin:2.1:set '" + featureName + "'");

        if (!getGitflowInit().gitIsCleanWorkingTree()) {
            String msg = getMsgPrefix() + "Updating poms for " + featureName + " branch " + nonFeatureVersion + " version " + getMsgSuffix();
            getGitflowInit().executeLocal("git add -A .");
            String[] cmtPom = {"git", "commit", "-m", "\"" + msg + "\""};
            getGitflowInit().executeLocal(cmtPom);
        } else {
            getLog().error("Failed to update poms to the develop version " + nonFeatureVersion);
        }

        GitflowFeature gitflowFeature = new GitflowFeature();
        gitflowFeature.setInit(getGitflowInit());
        gitflowFeature.setMsgPrefix(getMsgPrefix());
        gitflowFeature.setMsgSuffix(getMsgSuffix());

        try {
            gitflowFeature.finish(featureName);
        } catch (GitflowException ge) {
            throw new MojoFailureException(ge.getMessage());
        } catch (GitflowMergeConflictException gmce) {
            throw new MojoFailureException(gmce.getMessage());
        }
    }

    private String promptForExistingFeatureName(List<String> featureBranches, String defaultFeatureName) throws MojoFailureException {
        String message = "Please select a feature branch to finish?";

        String name = "";
        try {
            name = prompter.prompt(message, featureBranches, defaultFeatureName);
        } catch (PrompterException e) {
            throw new MojoFailureException("Error reading feature name from command line " + e.getMessage());
        }

        return name;
    }

}