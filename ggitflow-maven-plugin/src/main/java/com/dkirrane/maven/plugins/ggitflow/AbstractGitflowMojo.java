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

import com.dkirrane.gitflow.groovy.GitflowInit;
import com.dkirrane.gitflow.groovy.ex.GitflowException;
import com.dkirrane.maven.plugins.ggitflow.util.MavenUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.shared.release.ReleaseResult;
import org.apache.maven.shared.release.env.DefaultReleaseEnvironment;
import org.apache.maven.shared.release.env.ReleaseEnvironment;
import org.apache.maven.shared.release.exec.MavenExecutor;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.jfrog.hudson.util.GenericArtifactVersion;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

public class AbstractGitflowMojo extends AbstractMojo {

    public final Pattern matchSnapshotRegex = Pattern.compile("-SNAPSHOT");

    public static final ImmutableList<String> DEFAULT_INSTALL_ARGS = ImmutableList.of(
            "-DinstallAtEnd=true");

    public static final ImmutableList<String> DEFAULT_DEPLOY_ARGS = ImmutableList.of(
            "-DdeployAtEnd=true",
            "-DretryFailedDeploymentCount=2");

    public static final Splitter PROFILES_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    public static final Joiner PROFILES_JOINER = Joiner.on(',').skipNulls();

    /**
     * Gitflow branches and prefixes to use.
     *
     * @since 1.2
     */
    @Parameter(defaultValue = "${prefixes}")
    protected Prefixes prefixes;

    /**
     * Message prefix used for any commits made by this plugin.
     *
     * @since 1.2
     */
    @Parameter(property = "msgPrefix", defaultValue = "", required = false)
    protected String msgPrefix;

    /**
     * Message suffix used for any commits made by this plugin.
     *
     * @since 1.2
     */
    @Parameter(property = "msgSuffix", defaultValue = "", required = false)
    protected String msgSuffix;

    /**
     * Component used to prompt for input.
     */
    @Component
    protected Prompter prompter;

    @Component
    protected Map<String, MavenExecutor> mavenExecutors;

    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * @parameter default-value="${project.artifacts}"
     * @required
     * @readonly
     */
    protected Collection artifacts;

    /**
     * @parameter expression="${localRepository}"
     */
    protected ArtifactRepository localRepository;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    protected List remoteArtifactRepositories;

//    @Component
//    protected MavenProjectBuilder projectBuilder;
//    @Component
//    protected DefaultProjectBuilder projectBuilder;
//    /**
//     * @parameter property="plugin"
//     * @required
//     */
//    @Component
//    protected PluginDescriptor pluginDescriptor;
//
    /**
     * The projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    protected List<MavenProject> reactorProjects;

    /**
     * The project builder
     */
    @Component
    private ProjectBuilder projectBuilder;

    /**
     * The project currently being build.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    @Component
    protected MavenProject project;

    /**
     * The current Maven session.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    @Component
    protected MavenSession session;

    /**
     * The Maven BuildPluginManager component.
     *
     * @component
     * @required
     */
    @Component
    protected BuildPluginManager pluginManager;

//    @Parameter(defaultValue = "${basedir}", readonly = true, required = true)
//    protected File basedir;
//    @Component
//    protected Settings settings;
    private GitflowInit init;

    protected final MavenProject getProject() {
        return project;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (null == project) {
            throw new NullPointerException("MavenProject is null");
        } else {
            getLog().debug("Gitflow pom  '" + project.getBasedir() + "'");
        }

        GitflowInit gitflowInit = getGitflowInit();

        gitflowInit.requireGitRepo();

        if (!gitflowInit.gitflowIsInitialized()) {
            try {
                gitflowInit.cmdDefault();
            } catch (GitflowException ex) {
                throw new MojoExecutionException("Failed to inintialise Gitflow " + ex.getMessage(), ex);
            }
        }

        gitflowInit.requireCleanWorkingTree();
    }

    public String getMsgPrefix() {
        return (StringUtils.isBlank(msgPrefix)) ? "" : msgPrefix + " ";
    }

    public String getMsgSuffix() {
        return (StringUtils.isBlank(msgSuffix)) ? "" : " " + msgSuffix;
    }

    protected final GitflowInit getGitflowInit() {
        if (null == init) {
            getLog().info("Initialising Gitflow");
            init = new GitflowInit();
            File basedir = getProject().getBasedir();
            getLog().debug("Setting base directory " + basedir);
            init.setRepoDir(basedir);
            init.setMasterBrnName(prefixes.getMasterBranch());
            init.setDevelopBrnName(prefixes.getDevelopBranch());
            init.setFeatureBrnPref(prefixes.getFeatureBranchPrefix());
            init.setReleaseBrnPref(prefixes.getReleaseBranchPrefix());
            init.setHotfixBrnPref(prefixes.getHotfixBranchPrefix());
            init.setSupportBrnPref(prefixes.getSupportBranchPrefix());
            init.setVersionTagPref(prefixes.getVersionTagPrefix());

            /* root Git directory may not be the pom directory */
            String gitRootPath = init.executeLocal("git rev-parse --show-toplevel"); 
            getLog().debug("Git repo top level " + gitRootPath);
            File baseGitDir = new File(gitRootPath);
            if (baseGitDir.isDirectory()) {
                getLog().debug("Setting git base directory " + baseGitDir);
                init.setRepoDir(baseGitDir);
            }
        }
        return init;
    }

    protected final void setVersion(String version, Boolean push) throws MojoExecutionException, MojoFailureException {
        getLog().debug("START org.codehaus.mojo:versions-maven-plugin:2.1:set '" + version + "'");
        MavenProject rootProject = MavenUtil.getRootProject(reactorProjects);
        session.setCurrentProject(rootProject);
        session.setProjects(reactorProjects);

        for (MavenProject mavenProject : reactorProjects) {
            getLog().debug("Calling versions-maven-plugin:set on " + mavenProject.getArtifactId());
            session.setCurrentProject(mavenProject);
            try {
                executeMojo(
                        plugin(
                                groupId("org.codehaus.mojo"),
                                artifactId("versions-maven-plugin"),
                                version("2.1")
                        ),
                        goal("set"),
                        configuration(
                                element(name("generateBackupPoms"), "false"),
                                element(name("newVersion"), version)
                        ),
                        executionEnvironment(
                                mavenProject,
                                session,
                                pluginManager
                        )
                );
            } catch (MojoExecutionException mee) {
                String rootCauseMessage = ExceptionUtils.getRootCauseMessage(mee);
                if (rootCauseMessage.contains("Project version is inherited from parent")) {
                    getLog().warn("Skipping versions-maven-plugin:set for project " + mavenProject.getArtifactId() + ". Project version is inherited from parent.");
                } else {
                    getLog().error("Cannot set "+mavenProject.getArtifactId()+" to version '"+version+ "'", mee);
                    throw mee;
                }
            }
        }
        getLog().debug("DONE org.codehaus.mojo:versions-maven-plugin:2.1:set '" + version + "'");

        if (!getGitflowInit().gitIsCleanWorkingTree()) {
            String msg = getMsgPrefix() + "Updating poms to version " + version + "" + getMsgSuffix();
            getGitflowInit().executeLocal("git add -A .");
            String[] cmtPom = {"git", "commit", "-m", "\"" + msg + "\""};
            getGitflowInit().executeLocal(cmtPom);

            String currentBranch = getGitflowInit().gitCurrentBranch();
            if (push && getGitflowInit().gitRemoteBranchExists(currentBranch)) {
                String origin = getGitflowInit().getOrigin();
                String[] cmtPush = {"git", "push", origin, currentBranch};
                Integer exitCode = getGitflowInit().executeRemote(cmtPush);
                if (exitCode != 0) {
                    throw new MojoExecutionException("Failed to push version change " + version + " to origin. ExitCode:" + exitCode);
                }
            }
        }
        /* We don't want to fail maybe the version was manually set correctly */
//        else {
//            throw new MojoFailureException("Failed to update poms to version " + version);
//        }
    }

    protected final void setNextVersions(Boolean allowSnapshots, Boolean updateParent, String includes) throws MojoExecutionException, MojoFailureException {
        getLog().debug("setNextVersions");
        MavenProject rootProject = MavenUtil.getRootProject(reactorProjects);
        session.setCurrentProject(rootProject);
        session.setProjects(reactorProjects);

        if (updateParent) {
            getLog().debug("START org.codehaus.mojo:versions-maven-plugin:2.1:update-parent updateParent=" + updateParent);
            executeMojo(
                    plugin(
                            groupId("org.codehaus.mojo"),
                            artifactId("versions-maven-plugin"),
                            version("2.1")
                    ),
                    goal("update-parent"),
                    configuration(
                            element(name("generateBackupPoms"), "false"),
                            element(name("allowSnapshots"), allowSnapshots.toString())
                    ),
                    executionEnvironment(
                            rootProject,
                            session,
                            pluginManager
                    )
            );
            getLog().debug("DONE org.codehaus.mojo:versions-maven-plugin:2.1:update-parent");
        }

        if (!StringUtils.isBlank(includes)) {
            getLog().debug("START org.codehaus.mojo:versions-maven-plugin:2.1:use-next-versions allowSnapshots=" + allowSnapshots);
            for (MavenProject mavenProject : reactorProjects) {
                getLog().debug("Calling use-next-versions on " + mavenProject.getArtifactId());
                session.setCurrentProject(mavenProject);
                executeMojo(
                        plugin(
                                groupId("org.codehaus.mojo"),
                                artifactId("versions-maven-plugin"),
                                version("2.1")
                        ),
                        goal("use-next-versions"),
                        configuration(
                                element(name("generateBackupPoms"), "false"),
                                element(name("allowSnapshots"), allowSnapshots.toString()),
                                element(name("includesList"), includes)
                        ),
                        executionEnvironment(
                                mavenProject,
                                session,
                                pluginManager
                        )
                );
            }
            getLog().debug("DONE org.codehaus.mojo:versions-maven-plugin:2.1:use-next-versions");
        } else {
            getLog().warn("Parameter <includes> is not set. Skipping dependency updates");
        }

        if (!getGitflowInit().gitIsCleanWorkingTree()) {
            String msg;
            if (allowSnapshots) {
                msg = getMsgPrefix() + "Replaces any release versions with the next snapshot version (if it has been deployed)." + getMsgSuffix();
            } else {
                msg = getMsgPrefix() + "Replaces snapshot versions with the corresponding release version" + getMsgSuffix();
            }

            getGitflowInit().executeLocal("git add -A .");
            String[] cmtPom = {"git", "commit", "-m", "\"" + msg + "\""};
            getGitflowInit().executeLocal(cmtPom);

            String currentBranch = getGitflowInit().gitCurrentBranch();
            if (getGitflowInit().gitRemoteBranchExists(currentBranch)) {
                String origin = getGitflowInit().getOrigin();
                String[] cmtPush = {"git", "push", origin, currentBranch};
                Integer exitCode = getGitflowInit().executeRemote(cmtPush);
                if (exitCode != 0) {
                    throw new MojoExecutionException("Failed to push version change to origin. ExitCode:" + exitCode);
                }
            }
        }
    }

    protected final void runGoals(String goals, List<String> additionalArgs) throws MojoExecutionException, MojoFailureException {
        getLog().debug("START executing " + goals + " with args " + additionalArgs);

        MavenProject rootProject = MavenUtil.getRootProject(reactorProjects);
        File basedir = rootProject.getBasedir();

        ReleaseResult result = new ReleaseResult();
        ReleaseEnvironment env = new DefaultReleaseEnvironment();
        env.setSettings(session.getSettings());
        MavenExecutor mavenExecutor = mavenExecutors.get(env.getMavenExecutorId());

        Joiner joiner = Joiner.on(" ").skipNulls();
        String additionalArguments = joiner.join(additionalArgs);
        getLog().debug("additionalArguments " + additionalArguments);

        try {
            mavenExecutor.executeGoals(basedir, goals, env, false, additionalArguments, result);
        } catch (MavenExecutorException ex) {
            throw new MojoExecutionException(result.getOutput(), ex);
        }
        getLog().debug("DONE executing " + goals);
    }

    protected final String getReleaseVersion(String version) throws MojoFailureException {
        getLog().debug("Current Develop version '" + version + "'");

        GenericArtifactVersion artifactVersion = new GenericArtifactVersion(version);

        String primaryNumbersAsString = artifactVersion.getPrimaryNumbersAsString();
        String annotationAsString = artifactVersion.getAnnotationAsString();
        String buildSpecifierAsString = artifactVersion.getBuildSpecifierAsString();

        final StringBuilder result = new StringBuilder(30);
        result.append(primaryNumbersAsString).append(annotationAsString);

        if (!StringUtils.isBlank(buildSpecifierAsString)) {
            getLog().debug("Removing build specifier " + buildSpecifierAsString + " from version " + version);
        }

        return result.toString();
    }

    protected final void checkForSnapshotDependencies() throws MojoExecutionException {
        getLog().info("Checking for SNAPSHOT dependencies");
        Boolean hasDepSnapshots = false;
        Boolean hasParentSnapshot = false;
        for (MavenProject mavenProject : reactorProjects) {
            String artifactId = mavenProject.getArtifactId();

            /* Check <parent> */
            Artifact parentArtifact = mavenProject.getParentArtifact();
            if (parentArtifact != null && parentArtifact.isSnapshot()) {
                getLog().error("Parent of project " + artifactId + " is a SNAPSHOT " + parentArtifact.getId());
                hasParentSnapshot = true;
            }

            /* Check <dependencyManagement> */
            DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
            if (null != dependencyManagement) {
                if (checkForSnapshot(artifactId, dependencyManagement.getDependencies())) {
                    hasDepSnapshots = true;
                }
            }

            /* Check <dependencies> */
            if (checkForSnapshot(artifactId, mavenProject.getDependencies())) {
                hasDepSnapshots = true;
            }
        }
        if (hasParentSnapshot || hasDepSnapshots) {
            throw new MojoExecutionException("Cannot release because SNAPSHOT dependencies exist");
        }
    }

    private boolean checkForSnapshot(String artifactId, List<Dependency> dependencies) throws MojoExecutionException {
        Boolean hasSnapshotDependency = false;
        for (Dependency dependency : dependencies) {
            String version = dependency.getVersion();
            Matcher versionMatcher = matchSnapshotRegex.matcher(version);
            if (versionMatcher.find() && versionMatcher.end() == version.length()) {
                getLog().error("Project " + artifactId + " contains SNAPSHOT dependency: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + version);
                hasSnapshotDependency = true;
            }
        }
        return hasSnapshotDependency;
    }

    protected final void reloadReactorProjects() throws MojoExecutionException {
        getLog().debug("Reloading poms...");

        List<MavenProject> newReactorProjects;
        try {
            newReactorProjects = buildReactorProjects();
        } catch (ProjectBuildingException e) {
            getLog().error("Re-parse aborted due to malformed pom.xml file(s)", e);
            throw new MojoExecutionException("Re-parse aborted due to malformed pom.xml file(s)", e);
        } catch (CycleDetectedException e) {
            getLog().error("Re-parse aborted due to dependency cycle in project model", e);
            throw new MojoExecutionException("Re-parse aborted due to dependency cycle in project model", e);
        } catch (DuplicateProjectException e) {
            getLog().error("Re-parse aborted due to duplicate projects in project model", e);
            throw new MojoExecutionException("Re-parse aborted due to duplicate projects in project model", e);
        } catch (Exception e) {
            getLog().error("Re-parse aborted due a problem that prevented sorting the project model", e);
            throw new MojoExecutionException("Re-parse aborted due a problem that prevented sorting the project model", e);
        }
        MavenProject newProject = findProject(newReactorProjects, this.project);
        if (newProject == null) {
            throw new MojoExecutionException("A pom.xml change appears to have removed " + this.project.getId() + " from the build plan.");
        }

        this.project = newProject;
        this.reactorProjects = newReactorProjects;

        getLog().debug("Reloading poms complete...");
    }

    private List<MavenProject> buildReactorProjects() throws Exception {

        List<MavenProject> projects = new ArrayList<MavenProject>();
        for (MavenProject p : reactorProjects) {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest();

            request.setProcessPlugins(false);
            request.setProfiles(request.getProfiles());
            request.setActiveProfileIds(session.getRequest().getActiveProfiles());
            request.setInactiveProfileIds(session.getRequest().getInactiveProfiles());
            request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
            request.setSystemProperties(session.getSystemProperties());
            request.setUserProperties(session.getUserProperties());
            request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
            request.setPluginArtifactRepositories(session.getRequest().getPluginArtifactRepositories());
            request.setRepositorySession(session.getRepositorySession());
            request.setLocalRepository(localRepository);
            request.setBuildStartTime(session.getRequest().getStartTime());
            request.setResolveDependencies(false);
            request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_STRICT);
            projects.add(projectBuilder.build(p.getFile(), request).getProject());
        }
        return new ProjectSorter(projects).getSortedProjects();
    }

    private MavenProject findProject(List<MavenProject> newReactorProjects, MavenProject oldProject) {
        for (MavenProject newProject : newReactorProjects) {
            if (oldProject.getGroupId().equals(newProject.getGroupId())
                    && oldProject.getArtifactId().equals(newProject.getArtifactId())) {
                return newProject;
            }
        }
        return null;
    }

}
