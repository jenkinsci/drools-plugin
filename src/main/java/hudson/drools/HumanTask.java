package hudson.drools;

import hudson.model.AbstractModelObject;
import hudson.model.BooleanParameterValue;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class HumanTask extends AbstractModelObject implements Serializable,
		AccessControlled {

	private static final long serialVersionUID = 4611515699313881698L;

	private final List<ParameterDefinition> parameterDefinitions = new ArrayList<ParameterDefinition>();
	private final String displayName;
	private String actorId;
	private long workItemId;
	private transient DroolsRun run;
	private Status status = Status.NEW;

	public Status getStatus() {
		return status;
	}

	public enum Status {
		NEW, COMPLETED, CANCELED
	}

	private List<ParameterValue> answers;

	private boolean privateTask;

	public boolean isPrivateTask() {
		return privateTask;
	}

	public HumanTask(String displayName, boolean privateTask) {
		this.displayName = displayName;
		this.privateTask = privateTask;
	}

	public void doSubmit(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		List<ParameterValue> values = new ArrayList<ParameterValue>();

		JSONObject formData = req.getSubmittedForm();
		JSONArray a = JSONArray.fromObject(formData.get("parameter"));

		for (Object o : a) {
			JSONObject jo = (JSONObject) o;
			String name = jo.getString("name");

			ParameterDefinition d = getParameterDefinition(name);
			if (d == null)
				throw new IllegalArgumentException(
						"No such parameter definition: " + name);
			values.add(d.createValue(req, jo));
		}

		Map<String, Object> results = new HashMap<String, Object>();
		for (ParameterValue value : values) {
			if (value instanceof StringParameterValue) {
				results.put(value.getName(),
						((StringParameterValue) value).value);
			} else if (value instanceof BooleanParameterValue) {
				results.put(value.getName(),
						((BooleanParameterValue) value).value);
			}
		}

		Hudson.getInstance().getPlugin(PluginImpl.class).completeWorkItem(
				workItemId, results);

		this.answers = values;

		status = Status.COMPLETED;

		run.save();

		rsp.forwardToPreviousPage(req);
	}
	
	public void cancel() throws IOException {
		status = Status.CANCELED;
		run.save();
	}

	private ParameterDefinition getParameterDefinition(String name) {
		for (ParameterDefinition pd : parameterDefinitions) {
			if (name.equals(pd.getName())) {
				return pd;
			}
		}
		throw new IllegalArgumentException("Unknown parameter " + name);
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getSearchUrl() {
		return null;
	}

	public boolean isCompleted() {
		return status == Status.COMPLETED;
	}

	public boolean isCanceled() {
		return status == Status.CANCELED;
	}

	public boolean isNew() {
		return status == Status.NEW;
	}

	public long getWorkItemId() {
		return workItemId;
	}

	public void setWorkItemId(long workItemId) {
		this.workItemId = workItemId;
	}

	public List<ParameterDefinition> getParameterDefinitions() {
		return parameterDefinitions;
	}

	public List<ParameterValue> getAnswers() {
		return answers;
	}

	public DroolsRun getRun() {
		return run;
	}

	public void setRun(DroolsRun run) {
		this.run = run;
	}

	public String getUrl() {
		return run.getUrl() + "scriptExecution/" + workItemId;
	}

	@Override
	public String toString() {
		if (answers != null) {
			return String.format(
					"HumanTask(%s) parameters: %s answers: %s",
					displayName, parameterDefinitions, answers);
		} else {
			return String.format("HumanTask(%s) parameters: %s",
					displayName, parameterDefinitions);
		}
	}

	public String getActorId() {
		return actorId;
	}

	public User getActor() {
		return actorId != null ? User.get(actorId, false) : null;
	}

	public void setActorId(String actorId) {
		this.actorId = actorId;
	}
	
	public boolean canRead() {
		if (privateTask) {
			return actorId == null || actorId.equals(User.current().getId());
		} else {
			return true;
		}
	}

	public boolean canComplete() {
		if (status != Status.NEW) {
			return false;
		}

		// TODO should create a real ACL for this
		if (actorId != null && User.current() != null) {
			return /* hasPermission(Job.CONFIGURE) || */actorId.equals(User
					.current().getId());
		} else {
			return hasPermission(Job.BUILD);
		}
	}

	public void checkPermission(Permission permission)
			throws AccessDeniedException {
		getACL().checkPermission(permission);
	}

	public ACL getACL() {
		return run.getACL();
	}

	public boolean hasPermission(Permission permission) {
		return getACL().hasPermission(permission);
	}

	public static HumanTask getHumanTaskByWorkItemId(long workItemId) {
		for (DroolsProject p : Hudson.getInstance().getItems(
				DroolsProject.class)) {
			for (DroolsRun r : p.getBuilds()) {
				HumanTask humanTask = r.getHumanTask((int) workItemId);
				if (humanTask != null) {
					return humanTask;
				}
			}
		}
		return null;
	}
}
