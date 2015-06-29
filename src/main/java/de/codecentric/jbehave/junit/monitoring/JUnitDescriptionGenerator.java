package de.codecentric.jbehave.junit.monitoring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbehave.core.annotations.ScenarioType;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords.StartingWordNotFound;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.steps.BeforeOrAfterStep;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.StepCandidate;
import org.jbehave.core.steps.StepCollector.Stage;
import org.jbehave.core.steps.StepType;
import org.junit.runner.Description;

public class JUnitDescriptionGenerator {

	public static final String BEFORE_STORY_STEP_NAME = "@BeforeStory";
	public static final String AFTER_STORY_STEP_NAME = "@AfterStory";
	public static final String BEFORE_SCENARIO_STEP_NAME = "@BeforeScenario";
	public static final String AFTER_SCENARIO_STEP_NAME = "@AfterScenario";

	DescriptionTextUniquefier uniq = new DescriptionTextUniquefier();

	private int testCases;

	private List<StepCandidate> allCandidates = new ArrayList<>();

	private Map<ScenarioType, List<BeforeOrAfterStep>> beforeOrAfterScenario =
			new HashMap<>();
	{
		for (ScenarioType scenarioType : ScenarioType.values()) {
			beforeOrAfterScenario.put(scenarioType, new ArrayList<BeforeOrAfterStep>());
		}
	}

	private List<BeforeOrAfterStep> beforeOrAfterStory = new ArrayList<>();

	private final Configuration configuration;

	private String previousNonAndStep;

	public JUnitDescriptionGenerator(List<CandidateSteps> candidateSteps,
			Configuration configuration) {
		this.configuration = configuration;
		for (CandidateSteps candidateStep : candidateSteps) {
			allCandidates.addAll(candidateStep.listCandidates());
			for (ScenarioType scenarioType : ScenarioType.values()) {
				beforeOrAfterScenario.get(scenarioType).addAll(candidateStep.listBeforeOrAfterScenario(scenarioType));
			}
			beforeOrAfterStory.addAll(candidateStep.listBeforeOrAfterStory(false));
		}
	}

	public Description createDescriptionFrom(Story story) {
		Description storyDescription = createDescriptionForStory(story);
		addBeforeOrAfterStep(Stage.BEFORE, beforeOrAfterStory, storyDescription, BEFORE_STORY_STEP_NAME);
		addAllScenariosToDescription(story, storyDescription);
		addBeforeOrAfterStep(Stage.AFTER, beforeOrAfterStory, storyDescription, AFTER_STORY_STEP_NAME);
		return storyDescription;
	}

	public Description createDescriptionFrom(Scenario scenario) {
		Description scenarioDescription = createDescriptionForScenario(scenario);
		if (hasExamples(scenario)) {
			insertDescriptionForExamples(scenario, scenarioDescription);
		} else {
			if (hasGivenStories(scenario)) {
				insertGivenStories(scenario, scenarioDescription);
			}
			addScenarioSteps(ScenarioType.NORMAL, scenario, scenarioDescription);
		}
		return scenarioDescription;
	}

	private void addScenarioSteps(ScenarioType scenarioType, Scenario scenario, Description scenarioDescription) {
		addBeforeOrAfterScenarioStep(scenarioType, Stage.BEFORE, scenarioDescription, BEFORE_SCENARIO_STEP_NAME);
		addStepsToExample(scenario, scenarioDescription);
		addBeforeOrAfterScenarioStep(scenarioType, Stage.AFTER, scenarioDescription, AFTER_SCENARIO_STEP_NAME);
	}

	private void addBeforeOrAfterScenarioStep(ScenarioType scenarioType, Stage stage, Description description,
			String stepName) {
		List<BeforeOrAfterStep> beforeOrAfterSteps = new ArrayList<>();
		beforeOrAfterSteps.addAll(beforeOrAfterScenario.get(scenarioType));
		beforeOrAfterSteps.addAll(beforeOrAfterScenario.get(ScenarioType.ANY));
		addBeforeOrAfterStep(stage, beforeOrAfterSteps, description, stepName);
	}

	private void addBeforeOrAfterStep(Stage stage, List<BeforeOrAfterStep> beforeOrAfterSteps, Description description,
			String stepName)
	{
		for (BeforeOrAfterStep beforeOrAfterStep : beforeOrAfterSteps) {
			if (beforeOrAfterStep.getStage() == stage) {
				testCases++;
				addBeforeOrAfterStep(beforeOrAfterStep, description, stepName);
				break;
			}
		}
	}

	private void addBeforeOrAfterStep(BeforeOrAfterStep beforeOrAfterStep, Description description, String stepName)
	{
		Method method = beforeOrAfterStep.getMethod();
		Description testDescription = Description.createTestDescription(method.getDeclaringClass(),
				getJunitSafeString(stepName), method.getAnnotations());
		description.addChild(testDescription);
	}

	public String getJunitSafeString(String string) {
		return uniq.getUniqueDescription(replaceLinebreaks(string)
				.replaceAll("[\\(\\)]", "|"));
	}

	public int getTestCases() {
		return testCases;
	}

	private boolean hasGivenStories(Scenario scenario) {
		return !scenario.getGivenStories().getPaths().isEmpty();
	}

	private boolean hasExamples(Scenario scenario) {
		return isParameterized(scenario)
				&& !parameterNeededForGivenStories(scenario);
	}

	private boolean isParameterized(Scenario scenario) {
		ExamplesTable examplesTable = scenario.getExamplesTable();
		return examplesTable != null && examplesTable.getRowCount() > 0;
	}

	private boolean parameterNeededForGivenStories(Scenario scenario) {
		return scenario.getGivenStories().requireParameters();
	}

	private void insertGivenStories(Scenario scenario,
			Description scenarioDescription) {
		for (String path : scenario.getGivenStories().getPaths()) {
			addGivenStoryToScenario(scenarioDescription, path);
		}
	}

	private void addGivenStoryToScenario(Description scenarioDescription,
			String path) {
		scenarioDescription.addChild(Description
				.createSuiteDescription(getJunitSafeString(getFilename(path))));
		testCases++;
	}

	private String getFilename(String path) {
		return path.substring(path.lastIndexOf("/") + 1, path.length()).split(
				"#")[0];
	}

	private void insertDescriptionForExamples(Scenario scenario,
			Description scenarioDescription) {
		for (Map<String, String> row : scenario.getExamplesTable().getRows()) {
			Description exampleRowDescription = Description.createSuiteDescription(
							configuration.keywords().examplesTableRow() + " " + row,
							(Annotation[]) null);
			scenarioDescription.addChild(exampleRowDescription);
			if (hasGivenStories(scenario)) {
				insertGivenStories(scenario, exampleRowDescription);
			}
			addScenarioSteps(ScenarioType.EXAMPLE, scenario, exampleRowDescription);
		}
	}

	private void addStepsToExample(Scenario scenario, Description description) {
		addSteps(description, scenario.getSteps());
	}

	private void addSteps(Description description, List<String> steps) {
		previousNonAndStep = null;
		for (String stringStep : steps) {
			String stringStepOneLine = stripLinebreaks(stringStep);
			StepCandidate matchingStep = findMatchingStep(stringStep);
			if (matchingStep == null) {
				addNonExistingStep(description, stringStepOneLine, stringStep);
			} else {
				addExistingStep(description, stringStepOneLine, matchingStep);
			}
		}
	}

	private void addExistingStep(Description description,
			String stringStepOneLine, StepCandidate matchingStep) {
		if (matchingStep.isComposite()) {
			addCompositeSteps(description, stringStepOneLine, matchingStep);
		} else {
			addRegularStep(description, stringStepOneLine, matchingStep);
		}
	}

	private void addNonExistingStep(Description description, String stringStepOneLine,
			String stringStep) {
		try {
			if (configuration.keywords().stepTypeFor(stringStep) == StepType.IGNORABLE) {
				addIgnorableStep(description, stringStepOneLine);
			} else {
				addPendingStep(description, stringStepOneLine);
			}
		} catch (StartingWordNotFound e) {
			// WHAT NOW?
		}
	}

	private void addIgnorableStep(Description description, String stringStep) {
		testCases++;
		description.addChild(Description.createSuiteDescription(stringStep));
	}

	private void addPendingStep(Description description, String stringStep) {
		testCases++;
		description.addChild(Description.createSuiteDescription(getJunitSafeString("[PENDING] "
						+ stringStep)));
	}

	private void addRegularStep(Description description, String stringStep,
			StepCandidate step) {
		testCases++;
		// JUnit and the Eclipse JUnit view needs to be touched/fixed in order
		// to make the JUnit view
		// jump to the corresponding test method accordingly. For now we have to
		// live, that we end up in
		// the correct class.
		description.addChild(Description.createTestDescription(step.getStepsType(),
				getJunitSafeString(stringStep)));
	}

	private void addCompositeSteps(Description description, String stringStep,
			StepCandidate step) {
		Description testDescription = Description
				.createSuiteDescription(getJunitSafeString(stringStep));
		addSteps(testDescription, Arrays.asList(step.composedSteps()));
		description.addChild(testDescription);
	}

	private void addAllScenariosToDescription(Story story,
			Description storyDescription) {
		for (Scenario scenario : story.getScenarios()) {
			storyDescription.addChild(createDescriptionFrom(scenario));
		}
	}

	private StepCandidate findMatchingStep(String stringStep) {
		for (StepCandidate step : allCandidates) {
			if (step.matches(stringStep, previousNonAndStep)) {
				if (step.getStepType() != StepType.AND) {
					previousNonAndStep = step.getStartingWord() + " ";
				}
				return step;
			}
		}
		return null;
	}

	private String stripLinebreaks(String stringStep) {
		if (stringStep.indexOf('\n') != -1) {
			return stringStep.substring(0, stringStep.indexOf('\n'));
		}
		return stringStep;
	}

	private String replaceLinebreaks(String string) {
		return string.replaceAll("\r", "\n")
				.replaceAll("\n{2,}", "\n").replaceAll("\n", ", ");
	}

	private Description createDescriptionForStory(Story story) {
		return Description.createSuiteDescription(getJunitSafeString(story.getName()));
	}

	private Description createDescriptionForScenario(Scenario scenario) {
		return Description.createSuiteDescription(configuration.keywords().scenario() + " "
						+ getJunitSafeString(scenario.getTitle()));
	}
}
