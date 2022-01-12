# Developing The Reader

In developing the `cgiar-reader` there are three pieces: `rules` (and corresponding tests), `taxonomy`, and `actions`. The REST API and its endpoints are already defined. For information on how annotations are generated for use in rules, or how to use alternate processors, see the [Annotations](./annotations.md) section.

## Rules

Before developing a new rule or set of related rules, it is best to first define tests which describe the expected behavior of the rule(s). To develop tests follow the [Testing](./test.md) section.

Rules in the `cgiar-reader` are defined using Odin. For an in depth look at Odin and writing a grammar, please see the [manual](https://arxiv.org/pdf/1509.07513.pdf).

The `cgiar-reader` has two grammars, `entities` and `events`. The grammars can be found under `reader/grammars/cgiar`. An identical set of grammars can be found under `reader/src/main/resources/ai/lum/reader/grammars/cgiar`, however, when developing the grammars the user should only modify the top level grammars. These will later be edited and copied to the `src` grammars via action.

Rules can be developed with live reloading following the instructions in the [Development/Install](./install.md) section.

### Writing Rules

Odin rules are written in yaml and run over annotated text (the Odin manual includes a gentle introduction to [YAML syntax](https://arxiv.org/pdf/1509.07513.pdf#subsection.4.1)). Annotated text is produced using an external NLP service (such as `StanfordCoreNLP` or `SpaCY`), however, [Penn Tags](https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html) and [Universal Dependencies](https://universaldependencies.org/) are always included in the annotations.

There are two types of rules, `token` and `dependency`. If the type is not specified it defaults to `type=dependency`. Token rules are defined over the set of tokens and their values, while dependency rules are defined over the set of dependencies.

**Token rule**

If you wanted to label all tokens which carry the NER tag "LOCATION" as "Location," the following rule can be used.

```yaml
- name: ner-loc
  label: Location
  priority: 1
  type: token
  pattern: |
    [entity=LOCATION]+
```

!!! note
    Rules can be given a priority which determines the order in which rules apply. For example, a rule with `priority: 1` will only be run on the first iteration, whereas a rule with `priority: "2+"` will be run on all iterations following the first.

For example, given the text "How many F16 engines are heading to The United Kingdom?" the sequence "The United Kingdom" would be labeled `Location` by this rule.

**Graph traversal rule**

If you wanted to capture a "risk of" event, you could start with a simple rule defining a traversal over a syntactic dependency graph such as the following:

```yaml
- name: risk-of
    label: RiskOf
    example: "What is the risk of spoilage for frozen fish heading to Dubai on August 24th 2020?"
    pattern: |
      trigger = [lemma=risk] of
      type:Entity = nmod_of
```

This rule finds all words whose lemma is "risk" and if followed by "of" labels the sequence as a trigger of a `RiskOf` event, which is given a `type` defined by the dependency relation "nmod_of." In the provided example "spoilage" would be labeled as the `type` of the `RiskOf` event.

!!! note
    When the rules are run on text, a JSON file of labeled `mentions` is generated. Mentions are the matches found by the rules within the text, and the labels are included in a heiarchy defined in the `Taxonomy`.

## Taxonomy

The `taxonomy` is a set of heiarchical relationships between mention labels. Like the grammars, the taxonomy can be found in two places but only the top level taxonomy should be modified in development.

A sample of the `cgiar-reader` taxonomy can be seen here:

```yaml
- Measurement:
  - Unit
  - NumericExpression:
    - Quantity
```

## Actions

Generally, when developing rules there should be no need to change the existing actions. However, if it is necessary, new actions or modifications to existing actions can be made in [`reader/src/main/scala/ai/lum/mr/cgiar/odin/CustomActions.scala`](https://github.com/lum-ai/cgiar-reader/blob/master/reader/src/main/scala/ai/lum/mr/cgiar/odin/CustomActions.scala). For more information about actions, see [How it Works](./howitworks.md).
