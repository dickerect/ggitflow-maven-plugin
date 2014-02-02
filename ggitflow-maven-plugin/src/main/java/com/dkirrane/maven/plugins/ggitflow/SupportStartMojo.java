/*
 * Copyright 2014 desmondkirrane.
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

import com.dkirrane.gitflow.groovy.GitflowSupport;
import com.dkirrane.gitflow.groovy.ex.GitflowException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.jfrog.hudson.util.GenericArtifactVersion;
import static org.jfrog.hudson.util.GenericArtifactVersion.SNAPSHOT_QUALIFIER;

/**
 *
 */
@Mojo(name = "support-start", aggregator = true, defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class SupportStartMojo extends AbstractGitflowMojo {

    @Parameter(property = "startCommit", defaultValue = "")
    private String startCommit;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        String prefix = getGitflowInit().getSupportBranchPrefix();

        List<String> releaseBranches = getGitflowInit().gitLocalReleaseBranches();
        List<String> hotfixBranches = getGitflowInit().gitLocalHotfixBranches();
        if (releaseBranches.isEmpty() && hotfixBranches.isEmpty()) {
            throw new MojoFailureException("Could not find any release or hotfix branch to create support branch from!");
        }

        List<String> allBranches = new ArrayList<String>();
        allBranches.addAll(releaseBranches);
        allBranches.addAll(hotfixBranches);
        String defaultBrn = (hotfixBranches.isEmpty()) ? releaseBranches.get(0) : hotfixBranches.get(0);
        String baseName = promptForExistingReleaseOrHotfixName(allBranches, defaultBrn);

        getGitflowInit().executeLocal("git checkout " + baseName);

        String supportVersion = getSupportVersion(project.getVersion());
        String supportSnapshotVersion = getSupportSnapshotVersion(project.getVersion());

        getLog().info("Starting support branch '" + supportVersion + "'");
        getLog().debug("msgPrefix '" + getMsgPrefix() + "'");
        getLog().debug("msgSuffix '" + getMsgSuffix() + "'");

        GitflowSupport gitflowSupport = new GitflowSupport();
        gitflowSupport.setInit(getGitflowInit());
        gitflowSupport.setMsgPrefix(getMsgPrefix());
        gitflowSupport.setMsgSuffix(getMsgSuffix());

        if (!StringUtils.isEmpty(startCommit)) {
            gitflowSupport.setStartCommit(startCommit);
        }

        try {
            gitflowSupport.start(supportVersion);
        } catch (GitflowException ge) {
            throw new MojoFailureException(ge.getMessage());
        }

        setVersion(supportSnapshotVersion);

        if (getGitflowInit().gitRemoteBranchExists(prefix + supportVersion)) {
            getGitflowInit().executeRemote("git push " + getGitflowInit().getOrigin() + " " + prefix + supportVersion);
        }
    }

    private String promptForExistingReleaseOrHotfixName(List<String> branches, String defaultBrnName) throws MojoFailureException {
        String message = "Please select a release or hotfix branch to start support from?";

        String name = "";
        try {
            name = prompter.prompt(message, branches, defaultBrnName);
        } catch (PrompterException e) {
            throw new MojoFailureException("Error reading selected branch name from command line " + e.getMessage());
        }

        return name;
    }

    private String getSupportVersion(String currentVersion) throws MojoFailureException {
        getLog().info("Project version '" + currentVersion + "'");

        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(currentVersion);

        artifactVersion.upgradeLeastSignificantNumber();

        return artifactVersion.toString();
    }

    private String getSupportSnapshotVersion(String currentVersion) throws MojoFailureException {
        getLog().info("Project version '" + currentVersion + "'");

        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(currentVersion);

        String primaryNumbersAsString = artifactVersion.getPrimaryNumbersAsString();
        String annotationAsString = artifactVersion.getAnnotationAsString();
        String buildSpecifierAsString = artifactVersion.getBuildSpecifierAsString();

        final StringBuilder result = new StringBuilder(30);
        result.append(primaryNumbersAsString).append(annotationAsString);

        if (StringUtils.isBlank(buildSpecifierAsString)) {
            getLog().warn("Adding build specifier " + SNAPSHOT_QUALIFIER + " to support version " + currentVersion);
            result.append('-').append(SNAPSHOT_QUALIFIER);
        }

        return result.toString();
    }
}