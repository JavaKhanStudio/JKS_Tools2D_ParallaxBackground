"""Rules a page tool applies only when their conditions hold (r378).

A fix that is right for one model on one kind of painting is wrong elsewhere: the twig tips Depth Anything V2 Small
judges as far as the sky are a fault in a tree's back layer, and the same thin dark strokes are a ridge's detail in
another. A Rule names the conditions it needs, each a question asked of the run (which model, which layer, what the
theme says, what a measure finds), and acts only when every one holds. Whatever it decided, the run records it:
the recipe says which rules were weighed on what, which conditions held, and what each did.

    rule = Rule("sky_twigs", "a tree's twig tips judged as far as the sky go back to the sky",
                when=[("the back layer", lambda c: c["k"] == 0), ...],
                act=lambda c: ...)   # changes c, returns how many px it moved
    records = weigh([rule], ctx, forced={"sky_twigs": False})

forced (NAME -> True/False, from a tool's --rule NAME=on|off) runs a rule whatever its conditions say, or never: a
test, or a page its conditions misjudge. A condition is asked only while the ones before it held, so put the cheap
ones first and the measures last; a measure a condition takes can leave its result in ctx for act.
"""


class Rule:
    def __init__(self, name, about, when, act):
        self.name, self.about, self.when, self.act = name, about, when, act

    def holds(self, ctx):
        """(every condition held, {condition: held}) for the conditions asked, in order, up to the first that failed."""
        asked = {}
        for label, test in self.when:
            asked[label] = bool(test(ctx))
            if not asked[label]:
                return False, asked
        return True, asked


def parse_forced(specs, rules):
    """--rule NAME=on|off values as {NAME: bool}; ValueError on a name no rule has or a value not on/off."""
    names = {r.name for r in rules}
    forced = {}
    for spec in specs or []:
        name, _, value = spec.partition("=")
        if name not in names:
            raise ValueError(f"--rule {spec}: no rule {name!r} (rules: {', '.join(sorted(names))})")
        if value not in ("on", "off"):
            raise ValueError(f"--rule {spec}: on or off")
        forced[name] = value == "on"
    return forced


def weigh(rules, ctx, forced=None):
    """Each rule asked of ctx and applied where it holds (or is forced on): a record per rule, for the recipe."""
    forced = forced or {}
    records = []
    for rule in rules:
        if rule.name in forced:
            applies, asked = forced[rule.name], {}
            if applies:  # a forced rule's measures still run: act reads what they leave in ctx
                for _, test in rule.when:
                    test(ctx)
        else:
            applies, asked = rule.holds(ctx)
        record = {"rule": rule.name, "applied": applies, "when": asked}
        if rule.name in forced:
            record["forced"] = "on" if applies else "off"
        if applies:
            record["moved"] = int(rule.act(ctx))
        records.append(record)
    return records
