# Testing

Tests should be developed before rules. When developing rules, tests can be run using the command:

```bash
sbt cleanTest
```

!!! note
    This command first copies the grammars to the `src` files before running the tests. To run the tests without updating the rules use the command ```sbt test```.

## Developing Tests

Testing is performed through two files:

`TestEntities.scala`  (reader/src/test/scala/ai/lum/mr/cgiar/entities/TestEntities.scala)
and `TestEvents.scala` (reader/src/test/scala/ai/lum/mr/cgiar/events/TestEvents.scala),
that utilize the testing functions defined in `TestUtils.scala` (reader/src/test/scala/ai/lum/mr/TestUtils.scala).

Both `TestEntities.scala` and `TestEvents.scala` call the same boolean function, `checkMention()`.
`checkMention` operates on two inputs: a sequence of Mentions (extracted by the `MachineReadingSystem` using the Odin rules in cgiar-reader), and a `MentionTestCase`.
`MentionTestCase` is split into two cases: `ExistsMentionTestCase`, and `ForAllMentionTestCase`. The names of the two cases indicate the quantifier applying to evaluation of the sequence of Mentions, i.e. find at least one Mention such that..., or insure that for all Mentions ... .
This different quantification is directly reflected in the evaluation procedure at the level of `checkMention`:

- `ExistsMentionTestCase`:

```scala
// At least one mention must meet the criteria
mentions.exists { m => em.check(m) }
```

- `ForAllMentionTestCase`:

```scala
// Every mention must meet the criteria
mentions.forall { m => em.check(m) }
```

All functions called by `checkMention` have their own `check()` boolean function.
`MentionTestCase` (either variety)'s `check` function calls the `check` functions of its `TextTestCase` and `LabelTestCase(s)` obligatorily, and calls `check` from `ArgTestCase` if present.
`ArgTestCase`, in turn, through its `check` function, evaluates the `check` functions of its own `TextTestCase`, `LabelTestCase`(s), and `RoleTestCase`(s).

The bottom-level functions `TextTestCase`, `LabelTestCase`, `RoleTestCase` each have two varieties indicated by prefix: `Positive-`/`Negative-`. As the names suggest, a `PositiveTextTestCase` returns true if its string argument matches the `text` value of the Mention submitted to its `check` function. A `NegativeTextTestCase` returns true if its string argument does NOT match the Mention's `text` field.

Entity and Event tests differ in that Entity tests cannot contain `ArgTestCase` or `RoleTestCase`, only `TextTestCase` and `LabelTestCase` checks.

The typical use of testing involves positive subtests embedded in an `ExistsMentionTestCase`, as in this example:

```scala
ExistsMentionTestCase( 
    labels = Seq(PositiveLabelTestCase("Vessel")),
    mentionSpan = PositiveTextTestCase("LNG Finima"),
    text = "LNG Finima"
)
```

To illustrate the potential utility of negative testing: the entity rules include patterns for dates, such as "12 Jun 2000". However, a possible confusion arises from the format of time-of-day expressions in military documents, where we might see "12 Jun 2000 hours." (i.e., 8 P.M. on June 12). Of course, the entity rules themselves have a variety of means available to enforce this restriction: negative lookahead in the date rule, higher-priority military-time rules, etc. But to test proper functioning, we can write tests that might either seek to verify that at least one Mention found in some piece of text has such-and-such properties, or deny that any Mentions have some undesirable configuration of properties.

Complexities arise because `MentionTestCase` and `ArgTestsCase` contain multiple subtests (`TextTestCase`, `LabelTestCase`, `RoleTestCase`). What combinations of polarities among subtests (e.g. all positive, all negative, or a mixture of positive and negative) fall within the expected range of use?
Consider first the simplest case, corresponding to Entity tests: `MentionTestCase` not containing any `ArgTestCase`.
Use for a mix of positive and negative tests:
(`PositiveLabelTestCase`, `NegativetextTestCase`),- make sure the given label is picked up but doesn't correspond to this string;
(`NegativeLabelTestCase`, `PositiveTextTestCase`)<--make sure this string is not read as...
Running with the above example, suppose the parent MentionTestCase has text "12 Jun 2000 hours". The `MentionTestCase` can either be `ExistsMentionTestCase`, or `ForAllMentionTestCase`.

Combinations of positive and negative subtests under `ForAllMentionTestCase` seem of little use. For this reason, and to avoid possible confusion, we have written into the function definitions the requirement that `ForAllMentionTestCase` scopes over all-negative immediate subtests. Below is a typical example of the proper use of all-negative subtests for `ForAllMentionTestCase`:

```scala
ForAllMentionTestCase(
    labels = Seq(NegativeLabelTestCase("WhatQuery")), 
    mentionSpan = NegativeTextTestCase("of ostrich feathers"), 
    text = "TEUs of ostrich feathers"
)
```

Combinations of positive and negative subtests under `ExistsMentionTestCase` are potentially useful. in keeping with the flexibility outlined for some `ArgTestCase`s below, we allow `ExistsMentionTestCase` to freely embed either polarity of `LabelTestCase` and `TextTestCase`. Example:

```scala
ExistsMentionTestCase( 
    labels = Seq(NegativeLabelTestCase("Date")), // wrong label
    mentionSpan = PositiveTextTestCase("LNG Finima"),
    text = "LNG Finima"
)
ExistsMentionTestCase( 
    labels = Seq(PositiveLabelTestCase("Vessel")),
    mentionSpan = NegativeTextTestCase("Finima"), // mentionSpan doesn't match text
    text = "LNG Finima"
)
```

`ArgTestCase` polarity adds a further layer of potential complexity. To simplify use, we restrict the polarity of `ArgTestCase` itself to be strictly Negative under `ForAllMentionTestCase`, just like all its immediate subtests.: `ForAllMentionTestCase`(`NegativeLabelTestCase`, `NegativeTextTestCase`, `NegativeArgTestCase`(...)). In this configuration, we restrict `NegativeArgTestCase` to embed strictly positive subtests: `PositiveTextTestCase`, `PositiveLabelTestCase`, and `PositiveRoleTestCase` . Allowing `NegativeArgTestCase` to embed negative subtests, or a mix of positive and negative subtests, adds unwanted complexity unrewarded by clear use cases. The example below illustrates the intended use:

```scala
ForAllMentionTestCase(
    labels = Seq(NegativeLabelTestCase("CargoQuery"), NegativeLabelTestCase("QuantityQuery")),
    text = "Some zebras are galloping to Scotland from Zimbabwe",
    mentionSpan = NegativeTextTestCase("Some zebras are heading to Scotland from Zimbabwe"),
    args = List( 
        NegativeArgTestCase(
            role = PositiveRoleTestCase("need"),
            labels = Seq(PositiveLabelTestCase("QuantifiedCargo")),
            text = PositiveTextTestCase("zebras")
        )
    )
)
```

`ExistsMentionTestCase` may embed `PositiveArgTestCase` and/or `NegativeArgTestCase`:

```scala
ExistsMentionTestCase(
    labels = Seq(PositiveLabelTestCase("Transport")),
    mentionSpan = PositiveTextTestCase("Frozen food that arrived before September 21st 2020 but after September 28th 2020"),
    text = "Frozen food that arrived before September 21st 2020 but after September 28th 2020.",
    args = List(
        NegativeArgTestCase(
            role = PositiveRoleTestCase("need"),
            labels = Seq(PositiveLabelTestCase("BeforeTime"), PositiveLabelTestCase("TimeExpression")),
            text = PositiveTextTestCase("before September 21st 2020")
        ),
        PositiveArgTestCase(
            role = PositiveRoleTestCase("time"),
            labels = Seq(PositiveLabelTestCase("AfterTime"), PositiveLabelTestCase("TimeExpression")),
            text = PositiveTextTestCase("after September 28th 2020")
        )
    ),
),
```

To allow desired flexibility with easily-understood uses, we allow the subtests within `PositiveArgTestCase` (only) to be positive or negative, independently of other subtests within the same `PositiveArgTestCase`. The example below illustrates:

```scala
// Negative Label test; positive Text, Role tests.
ExistsMentionTestCase(
    labels = Seq(PositiveLabelTestCase("Transport")),
    mentionSpan = PositiveTextTestCase("Frozen food that arrived before September 21st 2020 but after September 28th 2020"),
    text = "Frozen food that arrived before September 21st 2020 but after September 28th 2020.",
    args = List(
        PositiveArgTestCase(
            role = PositiveRoleTestCase("time"),
            labels = Seq(NegativeLabelTestCase("AfterTime")),
            text = PositiveTextTestCase("before September 21st 2020")
        ),
    )
),
// Negative Text test; positive Label, Role tests.
ExistsMentionTestCase(
    labels = Seq(PositiveLabelTestCase("Transport")),
    mentionSpan = PositiveTextTestCase("Frozen food that arrived before September 21st 2020 but after September 28th 2020"),
    text = "Frozen food that arrived before September 21st 2020 but after September 28th 2020.",
    args = List(
        PositiveArgTestCase(
            role = PositiveRoleTestCase("time"),
            labels = Seq(PositiveLabelTestCase("BeforeTime")),
            text = NegativeTextTestCase("after September 21st 2020")
        ),
    )
)
```