# Feature Decision Template

Use this decision model:

1. What layer should own the behavior?
2. Is there already an interface/use-case/service that should own it?
3. Does the feature require:
    - new interface
    - new use-case
    - new adapter
    - new ViewModel state/event
4. What is the smallest architecture-compliant implementation?
5. What would be the common anti-pattern implementation?
6. Why should that anti-pattern be avoided here?