package com.dkirrane.maven.plugins.ggitflow;

import java.io.IOException;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Before;
import org.junit.Test;

import com.dkirrane.maven.plugins.ggitflow.prompt.PrompterImpl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ReleaseStartMojoTest {

    private static final String CURRENT_DEVELOPMENT_VERSION = "1.1.0-SNAPSHOT";
    protected ReleaseStartMojoStub releaseStartMojoStub;

    @Before
    public void setup() {

        releaseStartMojoStub = new ReleaseStartMojoStub();
    }
    @Test
    public void should_use_development_version_from_mojo_property() throws MojoFailureException, MojoExecutionException {
        String developmentVersion = "1.2.0-SNAPSHOT";
        releaseStartMojoStub.setDevelopmentVersion(developmentVersion);
        String nextDevelopmentVersion = releaseStartMojoStub.getNextDevelopVersion(CURRENT_DEVELOPMENT_VERSION);
        assertThat(nextDevelopmentVersion, is(developmentVersion));
    }

    @Test
    public void should_use_calculated_development_version() throws MojoFailureException, MojoExecutionException {
        releaseStartMojoStub.session = createNonInteractiveMavenSession();
        String nextDevelopVersion = releaseStartMojoStub.getNextDevelopVersion(CURRENT_DEVELOPMENT_VERSION);
        assertThat(nextDevelopVersion, is("1.1.1-SNAPSHOT"));
    }

    @Test
    public void should_use_development_version_from_prompt() throws IOException, MojoExecutionException {
        releaseStartMojoStub.session= createInteractiveMavenSession();

        String stubbedPromptResponse = "1.83.0-SNAPSHOT";
        releaseStartMojoStub.prompter = new PrompterStub(stubbedPromptResponse);

        String nextDevelopVersion = releaseStartMojoStub.getNextDevelopVersion(CURRENT_DEVELOPMENT_VERSION);
        assertThat(nextDevelopVersion, is(stubbedPromptResponse));

    }

    private MavenSession createNonInteractiveMavenSession() {
        boolean interactive = false;
        return createMavenSession(interactive);
    }

    private MavenSession createInteractiveMavenSession() {
        boolean interactive = true;
        return createMavenSession(interactive);
    }

    private MavenSession createMavenSession(boolean interactive) {
        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setInteractiveMode(interactive);
        return new MavenSession(null, null, request, null);
    }


    class PrompterStub extends PrompterImpl {

        private String stubbedPromptResponse;

        public PrompterStub(String stubbedPromptResponse) throws IOException {
            this.stubbedPromptResponse = stubbedPromptResponse;
        }

        @Override
        public String promptWithDefault(String message, String defaultValue) throws IOException {
            return stubbedPromptResponse;
        }
    }
    class ReleaseStartMojoStub extends ReleaseStartMojo {

        void setDevelopmentVersion(String developmentVersion) {
            this.developmentVersion=developmentVersion;
        }
    }
}