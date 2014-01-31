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

import static com.dkirrane.gitflow.groovy.Constants.*;
import com.dkirrane.gitflow.groovy.GitflowFeature;
import com.dkirrane.gitflow.groovy.ex.GitflowException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 *
 */
@Mojo(name = "feature-start", aggregator = true, defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class FeatureStartMojo extends AbstractFeatureMojo {

    @Parameter(property = "startCommit", defaultValue = "")
    private String startCommit;    

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        if (StringUtils.isBlank(featureName)) {
            String prefix = getFeatureBranchPrefix();
            System.out.println("prefix = " + prefix);
            System.out.println("prompter = " + prompter);
            String message = "What is the feature branch name? " + prefix;
            System.out.println("message = " + message);
            try {
                featureName = prompter.prompt(message);
                if (StringUtils.isBlank(featureName)) {
                    throw new MojoFailureException("Parameter <featureName> cannot be null or empty.");
                }
            } catch (PrompterException ex) {
                throw new MojoExecutionException("Error reading feature name from command line " + ex.getMessage(), ex);
            }
        }
        featureName = getFeatureName(featureName);

        getLog().info("Starting feature '" + featureName + "'");
        getLog().info("msgPrefix '" + getMsgPrefix() + "'");
        getLog().info("msgSuffix '" + getMsgSuffix() + "'");

        GitflowFeature gitflowFeature = new GitflowFeature();
        gitflowFeature.setInit(getGitflowInit());
        gitflowFeature.setMsgPrefix(getMsgPrefix());
        gitflowFeature.setMsgSuffix(getMsgSuffix());

        try {
            gitflowFeature.start(featureName);
        } catch (GitflowException ge) {
            throw new MojoFailureException(ge.getMessage());
        }

        String version = project.getVersion();

        String featureVersion = getFeatureVersion(version, featureName);

        setVersion(featureVersion);
    }

    public String getFeatureName() {
        return featureName;
    }
}
