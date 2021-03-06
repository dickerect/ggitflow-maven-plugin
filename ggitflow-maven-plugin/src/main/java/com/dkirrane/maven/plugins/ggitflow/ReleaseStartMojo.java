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

import com.dkirrane.gitflow.groovy.GitflowRelease;
import com.dkirrane.gitflow.groovy.ex.GitCommandException;
import com.dkirrane.gitflow.groovy.ex.GitflowException;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.jfrog.hudson.util.GenericArtifactVersion;
import static org.jfrog.hudson.util.GenericArtifactVersion.SNAPSHOT_QUALIFIER;

/**
 * Creates a new release branch off of the develop branch.
 */
@Mojo(name = "release-start", aggregator = true)
public class ReleaseStartMojo extends AbstractReleaseMojo {

    /**
     * If the project has a parent with a <code>-SNAPSHOT</code> version it will
     * be replaced with the corresponding release version (if it has been
     * released). This action is performed on the release branch after it is
     * created.
     *
     * @since 1.2
     */
    @Parameter(property = "updateParent", defaultValue = "false", required = false)
    private boolean updateParent;

    /**
     * Any dependencies with a <code>-SNAPSHOT</code> version are replaced with
     * the corresponding release version (if it has been released). This action
     * is performed on the release branch after it is created.
     *
     * @since 1.2
     */
    @Parameter(property = "updateDependencies", defaultValue = "false", required = false)
    private boolean updateDependencies;

    /**
     * If <code>updateDependencies</code> is set, then this should contain a
     * comma separated list of artifact patterns to include. Follows the pattern <code>groupId:artifactId:type:classifier:version<code>
     *
     * @since 1.5
     */
    @Parameter(property = "includes", defaultValue = "", required = false)
    private String includes;

    /**
     * The commit to start the release branch from.
     *
     * @since 1.2
     */
    @Parameter(property = "startCommit", defaultValue = "", required = false)
    private String startCommit;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        /* Switch to develop branch and get its current version */
        getGitflowInit().executeLocal("git checkout " + getGitflowInit().getDevelopBranch());
        reloadReactorProjects();

        String developVersion = project.getVersion();
        getLog().info("Current develop version = " + developVersion);

        /* Get next development version */
        String nextDevelopVersion = getNextDevelopVersion(developVersion);
        if (session.getRequest().isInteractiveMode()) {
            try {
                nextDevelopVersion = prompter.promptWithDefault("Please enter the next development version? ", nextDevelopVersion);
            } catch (IOException ex) {
                exceptionMapper.handle(new MojoExecutionException("Error reading next development version from command line " + ex.getMessage(), ex));
            }
        }
        GenericArtifactVersion nextDevelopArtifactVersion = new GenericArtifactVersion(developVersion);
        getLog().debug("Next development version = " + nextDevelopArtifactVersion);

        /* Get suggested release version */
        String releaseVersion = getReleaseVersion(developVersion);
        getLog().debug("release version = " + releaseVersion);

        /* create release branch */
        String prefix = getReleaseBranchPrefix();
        if (!StringUtils.isBlank(releaseName)) {
            getLog().debug("Using releaseName passed  '" + releaseName + "'");
        } else if (session.getRequest().isInteractiveMode()) {
            try {
                releaseName = prompter.promptWithDefault("Please enter the release branch name? " + prefix, releaseVersion);
            } catch (IOException ex) {
                exceptionMapper.handle(new MojoExecutionException("Error reading release name from command line " + ex.getMessage(), ex));
            }
        } else {
            releaseName = releaseVersion;
        }

        releaseName = trimReleaseName(releaseName);

        if (StringUtils.isBlank(releaseName)) {
            exceptionMapper.handle(new MojoFailureException("Parameter <releaseName> cannot be null or empty."));
        }

        GenericArtifactVersion releaseArtifactVersion;
        try {
            releaseArtifactVersion = new GenericArtifactVersion(releaseName);
            if (SNAPSHOT_QUALIFIER.equals(releaseArtifactVersion.getBuildSpecifier())) {
                throw new IllegalArgumentException("Parameter <releaseName> is not a release version as it contains SNAPSHOT build specifier");
            }
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Parameter <releaseName> value '" + releaseName + "' is not a valid Maven release version.");
        }

        getLog().info("Starting release '" + releaseName + "'");
        getLog().debug("msgPrefix '" + getMsgPrefix() + "'");
        getLog().debug("msgSuffix '" + getMsgSuffix() + "'");

        GitflowRelease gitflowRelease = new GitflowRelease();
        gitflowRelease.setInit(getGitflowInit());
        gitflowRelease.setMsgPrefix(getMsgPrefix());
        gitflowRelease.setMsgSuffix(getMsgSuffix());
        gitflowRelease.setPush(true);
        gitflowRelease.setStartCommit(startCommit);

        try {
            gitflowRelease.start(releaseName);
        } catch (GitCommandException gce) {
            String header = "Failed to run release start";
            exceptionMapper.handle(header, gce);
        } catch (GitflowException ge) {
            String header = "Failed to run release start";
            exceptionMapper.handle(header, ge);
        }

        // current branch should be the release branch
        String releaseBranch = getGitflowInit().gitCurrentBranch();
        if (!releaseBranch.startsWith(prefix)) {
            exceptionMapper.handle(new MojoFailureException("Failed to create release version."));
        }

        /* Update release branch dependencies to release version */
        if (updateDependencies) {
            reloadReactorProjects();
            setNextVersions(false, updateParent, includes);
        }

        // checkout develop branch and update it's version
        String developBranch = (String) getGitflowInit().getDevelopBrnName();
        getGitflowInit().executeLocal("git checkout " + developBranch);
        reloadReactorProjects();
        setVersion(nextDevelopVersion, developBranch, true);

        // checkout release branch again and update it's version to required release version
        getGitflowInit().executeLocal("git checkout " + releaseBranch);
        reloadReactorProjects();
        setVersion(releaseArtifactVersion.setBuildSpecifier(SNAPSHOT_QUALIFIER).toString(), releaseBranch, true);
    }

    private String getNextDevelopVersion(String developVersion) {
        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(developVersion);
        artifactVersion.upgradeLeastSignificantPrimaryNumber();

        return artifactVersion.toString();
    }
}
