package hudson.plugins.ghd;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public final class GitHubPublisher extends Recorder implements Describable<Publisher> {

    private String profileName;
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final List<Entry> entries = new ArrayList<Entry>();


    @DataBoundConstructor
    public GitHubPublisher() {
        super();
    }

    public GitHubPublisher(String profileName) {
        super();
        if (profileName == null) {
            // defaults to the first one
            GitHubProfile[] sites = DESCRIPTOR.getProfiles();
            if (sites.length > 0)
                profileName = sites[0].getName();
        }
        this.profileName = profileName;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public GitHubProfile getProfile() {
        GitHubProfile[] profiles = DESCRIPTOR.getProfiles();

        if (profileName == null && profiles.length > 0)
            // default
            return profiles[0];

        for (GitHubProfile profile : profiles) {
            if (profile.getName().equals(profileName))
                return profile;
        }
        return null;
    }

    public String getName() {
        return this.profileName;
    }

    public void setName(String profileName) {
        this.profileName = profileName;
    }

    protected void log(final PrintStream logger, final String message) {
        logger.println(StringUtils.defaultString(getDescriptor().getDisplayName()) + " " + message);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build,
                           Launcher launcher,
                           BuildListener listener)
            throws InterruptedException, IOException {

        if (build.getResult() == Result.FAILURE) {
            // build failed. don't post
            return true;
        }

        GitHubProfile profile = getProfile();
        if (profile == null) {
            log(listener.getLogger(), "No GitHub downloads section profile is configured.");
            build.setResult(Result.UNSTABLE);
            return true;
        }
        log(listener.getLogger(), "Using GitHub downloads section profile: " + profile.getName());
        try {
            Map<String, String> envVars = build.getEnvironment(listener);

            for (Entry entry : entries) {
                String expanded = Util.replaceMacro(entry.sourceFile, envVars);
                FilePath ws = build.getWorkspace();
                FilePath[] paths = ws.list(expanded);

                if (paths.length == 0) {
                    // try to do error diagnostics
                    log(listener.getLogger(), "No file(s) found: " + expanded);
                    String error = ws.validateAntFileMask(expanded);
                    if (error != null)
                        log(listener.getLogger(), error);
                }
                String destinationRepository = Util.replaceMacro(entry.destinationRepository, envVars);
                for (FilePath src : paths) {
                    log(listener.getLogger(), "destinationRepository=" + destinationRepository + ", file=" + src.getName());
                    profile.upload(destinationRepository, src);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to upload files"));
            build.setResult(Result.UNSTABLE);
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private final CopyOnWriteList<GitHubProfile> profiles = new CopyOnWriteList<GitHubProfile>();
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public DescriptorImpl(Class<? extends Publisher> clazz) {
            super(clazz);
            load();
        }

        public DescriptorImpl() {
            this(GitHubPublisher.class);
        }

        @Override
        public String getDisplayName() {
            return "Publish artifacts to GitHub downloads section";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/ghd/help.html";
        }

        @Override
        public GitHubPublisher newInstance(StaplerRequest req, net.sf.json.JSONObject formData) throws FormException {
            GitHubPublisher pub = new GitHubPublisher();
            req.bindParameters(pub, "ghd.");
            pub.getEntries().addAll(req.bindParametersToList(Entry.class, "ghd.entry."));
            return pub;
        }

        @Override
        public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
            profiles.replaceBy(req.bindParametersToList(GitHubProfile.class, "ghd."));
            save();
            return true;
        }



        public GitHubProfile[] getProfiles() {
            return profiles.toArray(new GitHubProfile[0]);
        }

        public FormValidation doLoginCheck(final StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String name = Util.fixEmpty(req.getParameter("name"));
            if (name == null) {// name is not entered yet
                return FormValidation.ok();

            }
            GitHubProfile profile = new GitHubProfile(name, req.getParameter("user"), req.getParameter("password"), null);

            try {
                profile.check();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                return FormValidation.error("Can't connect to GitHub service: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }
}
