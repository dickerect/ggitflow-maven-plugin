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

import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.jfrog.hudson.util.GenericArtifactVersion;

/**
 * Creates a tag on a support branch when releasing of a support branch. Note:
 * this does not merge support branch changes back to develop
 */
@Mojo(name = "support-tag", aggregator = true)
public class SupportTagMojo extends AbstractGitflowMojo {

    /**
     * If <code>true</code>, the tag can still get created even if
     * <code>-SNAPSHOT</code> dependencies exists in the pom.
     *
     * @since 2.3
     */
    @Parameter(property = "allowSnapshots", defaultValue = "false", required = false)
    private Boolean allowSnapshots;

    /**
     * If <code>true</code>, the support tag will be signed.
     *
     * @since 2.3
     */
    @Parameter(property = "sign", defaultValue = "false", required = false)
    private Boolean sign;

    /**
     * The GNU Privacy Guard (GPG) private key used to sign the tag.
     *
     * @since 2.3
     */
    @Parameter(property = "signingkey", defaultValue = "", required = false)
    private String signingkey;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        getLog().debug("Tagging support branch");

        /* Get release branch name */
        List<String> supportBranches = getGitflowInit().gitLocalSupportBranches();
        String supportBranch = "";
        if (supportBranches.isEmpty()) {
            throw new MojoFailureException("Could not find any local support branch!");
        } else if (supportBranches.size() == 1) {
            supportBranch = supportBranches.get(0);
        } else {
            supportBranch = promptForExistingSupportBranch(supportBranches, supportBranch);
        }

        getLog().info("Tagging support branch '" + supportBranch + "'");

        /* Switch to support branch and get its current version */
        getGitflowInit().executeLocal("git checkout " + supportBranch);
        reloadReactorProjects();
        String snapshotVersion = project.getVersion();

        /* Get release version for support tag */
        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(snapshotVersion);
        String supportVersion;
        if ("SNAPSHOT".equals(artifactVersion.getBuildSpecifier())) {
            supportVersion = getReleaseVersion(project.getVersion());
        } else {
            supportVersion = project.getVersion();
        }
        getLog().debug("support version = " + supportVersion);

        getGitflowInit().requireTagAbsent(supportVersion);

        /* Set support branch to release version */
        boolean setVersion = setVersion(supportVersion, false, supportBranch);

        if (!allowSnapshots) {
            reloadReactorProjects();
            try {
                checkForSnapshotDependencies();
            } catch (MojoExecutionException mee) {
                // reset setVersion commit to allow user fix & push SNAPSHOT dependencies
                if (setVersion) {
                    getGitflowInit().executeLocal("git reset --hard HEAD~1");
                }
                throw mee;
            }
        }

        /* tag support branch */
        String tagMsg = "Support release version " + supportVersion;
        String tagName = supportVersion;
        String tagString = getGitflowInit().executeLocal("git tag -a -m \"" + tagMsg + "\" " + tagName + " " + supportBranch);
        getLog().debug("Git tag output = " + tagString);

        /* Increment support branch to next version */
        String nextSupportVersion = getNextSupportVersion(snapshotVersion);
        setVersion(nextSupportVersion, false, supportBranch);
        promptToPushSupportBranchAndTag(supportBranch, tagName);

    }

    private String promptForExistingSupportBranch(List<String> supportBranches, String defaultSupportBranch) throws MojoFailureException {
        String message = "Please select a support branch to tag?";

        String name = "";
        try {
            name = prompter.prompt(message, supportBranches, defaultSupportBranch);
        } catch (PrompterException e) {
            throw new MojoFailureException("Error reading support branch name from command line " + e.getMessage());
        }

        return name;
    }

    private void promptToPushSupportBranchAndTag(String supportBranch, String supportTag) throws MojoFailureException {
        String origin = getGitflowInit().getOrigin();
        String message = "Do you want to push " + supportBranch + " branch and support tag " + supportTag + " to " + origin + "? (y/N)";

        String answer = "";
        try {
            answer = prompter.prompt(message);
        } catch (PrompterException e) {
            throw new MojoFailureException("Error reading support branch name from command line " + e.getMessage());
        }

        if (answer.matches("/^([yY][eE][sS]|[yY])$/")) {
            if (getGitflowInit().gitRemoteBranchExists(supportBranch)) {
                getGitflowInit().executeRemote("git push " + origin + " " + supportBranch);
                getGitflowInit().executeRemote("git push " + origin + " " + supportTag);
            } else {
                getLog().info("");
                getLog().warn("===> Branch '" + supportBranch + "' does not exist on '" + origin + "'");
                getLog().warn("===> Once ready you MUST manually push '" + supportBranch + "' branch and support tag '" + supportTag + "' to '" + origin + "'");
                getLog().info("");
            }
        } else {
            getLog().info("");
            getLog().warn("===> Once ready you MUST manually push '" + supportBranch + "' branch and support tag '" + supportTag + "' to '" + origin + "'");
            getLog().info("");
        }
    }

    private String getNextSupportVersion(String currentVersion) throws MojoFailureException {
        getLog().debug("getNextSupportVersion from '" + currentVersion + "'");

        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(currentVersion);

        StringBuilder sb = new StringBuilder(10);
        Integer annotationRevision = artifactVersion.getAnnotationRevision();
        if (null == annotationRevision) {
            throw new MojoFailureException("Cannot find Maven buildNumber in support version " + currentVersion);
        }
        GenericArtifactVersion upgradeAnnotationRevision = artifactVersion.upgradeAnnotationRevision();
        return upgradeAnnotationRevision.toString();
    }
}