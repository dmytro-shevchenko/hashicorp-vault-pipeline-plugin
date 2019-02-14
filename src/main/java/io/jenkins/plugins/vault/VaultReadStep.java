package io.jenkins.plugins.vault;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.datapipe.jenkins.vault.VaultAccessor;
import com.datapipe.jenkins.vault.configuration.GlobalVaultConfiguration;
import com.datapipe.jenkins.vault.credentials.VaultCredential;
import com.datapipe.jenkins.vault.exception.VaultPluginException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class VaultReadStep extends Step {
    private String path;
    private String key;
    private String credentialsId;
    private String vaultUrl;
    private Boolean renew;
    private Integer renewHours;

    @DataBoundConstructor
    public VaultReadStep() {

    }

    @DataBoundSetter
    public void setPath(String path) {
        this.path = path;
    }

    @DataBoundSetter
    public void setKey(String key) {
        this.key = key;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setVaultUrl(String vaultUrl) {
        this.vaultUrl = vaultUrl;
    }

    @DataBoundSetter
    public void setRenew(Boolean renew) {
        this.renew = renew;
    }

    @DataBoundSetter
    public void setRenewHours(Integer renewHours) {
        this.renewHours = renewHours;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new VaultStepExecution(this, stepContext, this.renew, this.renewHours);
    }

    private static final class VaultStepExecution extends StepExecution {
        private static final long serialVersionUID = 1L;
        private transient final VaultReadStep step;
        private Boolean renew;
        private Integer renewHours;

        private VaultStepExecution(VaultReadStep step, StepContext context, Boolean renew, Integer renewHours) {
            super(context);
            this.step = step;
            this.renew = renew;
            this.renewHours = renewHours;
        }

        private EnvVars getEnvironment() throws Exception {
            Run run = getContext().get(Run.class);
            TaskListener taskListener = getContext().get(TaskListener.class);
            return run.getEnvironment(taskListener);
        }

        private VaultAccessor getAccessor(Run<?, ?> run, TaskListener listener) throws Exception {
            EnvVars environment = getEnvironment();
            GlobalVaultConfiguration vaultConfig = GlobalConfiguration.all().get(GlobalVaultConfiguration.class);
            String credentialsId = step.credentialsId == null || step.credentialsId.isEmpty() ? vaultConfig.getConfiguration().getVaultCredentialId() : Util.replaceMacro(step.credentialsId, environment);
            String vaultUrl = step.vaultUrl == null || step.vaultUrl.isEmpty() ? vaultConfig.getConfiguration().getVaultUrl() : Util.replaceMacro(step.vaultUrl, environment);

            listener.getLogger().append(String.format("using vault credentials \"%s\" and url \"%s\"", credentialsId, vaultUrl));

            VaultAccessor vaultAccessor = new VaultAccessor();
            vaultAccessor.init(vaultUrl);

            VaultCredential credentials = CredentialsProvider.findCredentialById(credentialsId, VaultCredential.class, run);

            if (credentials != null) {
                vaultAccessor.auth(credentials);
            }
            return vaultAccessor;
        }

        @Override
        public boolean start() throws Exception {
            try {
                EnvVars environment = getEnvironment();
                VaultAccessor accessor = getAccessor(getContext().get(Run.class), getContext().get(TaskListener.class));
                String value = accessor.read(Util.replaceMacro(step.path, environment)).getData().get(Util.replaceMacro(step.key, environment));
                if (Boolean.TRUE.equals(this.renew)) {
                    accessor.tokenRenew(this.renewHours);
                }
                getContext().onSuccess(value);
            } catch (VaultPluginException e) {
                getContext().onFailure(e);
            }
            return true;
        }

        @Override
        public void stop(@Nonnull Throwable throwable) throws Exception {
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<Class<?>>() {{ add(Run.class); add(TaskListener.class); }};
        }

        @Override
        public String getFunctionName() {
            return "vault";
        }
    }
}
