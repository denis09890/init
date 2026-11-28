# Classifier evaluation

How well does the rule-based classifier ([ADR 0003](adr/0003-rule-based-classification-first.md))
recognize early-career postings, their opportunity type and their domain? This document describes the
labelled set, how it is measured and what the numbers are. Every later classifier (an LLM-based one is
planned) is measured on the same set.

## The labelled set

- **Population.** Postings the public feed could show: open, and located in Romania or remote and open
  to it. On 2026-11-28 that was **320 postings** from 15 companies, of which the rules called **46** early
  career and **274** not.
- **Sample.** All 46 rules positives, plus **80** of the 274 negatives chosen at random but reproducibly
  (ordered by a hash of their key), so **126 postings** in total. Labelling every positive makes
  precision exact; sampling the negatives keeps the work small while still finding what the rules miss.
- **Export.** `GET /api/admin/evaluation/sample?negatives=80` (dev profile only) re-normalizes each
  posting's stored raw payload, so the sample shows exactly what the classifier reads. It is committed
  as `backend/src/test/resources/evaluation/sample.json`. Descriptions are cut to the 300-character
  public excerpt, like everything else in the repository.
- **Labels.** `backend/src/test/resources/evaluation/labels.json`, one per posting: early career (yes/no),
  opportunity type, domain, and a note where the decision is not obvious. *Early career* means suitable
  for a student or a graduate with 0–2 years of experience, entry-level jobs in any field included; a
  posting that asks for at least 2 years is not. When the excerpt was not enough, the live posting was
  read (9 postings; the notes say so).

## How it is measured

`RuleBasedClassifierEvaluationTests` runs the classifier on every labelled posting and writes the full
report, with confusion matrix and error lists, to `backend/target/evaluation-report.md`:

```powershell
cd backend
.\mvnw.cmd -B test "-Dtest=RuleBasedClassifierEvaluationTests"
```

For the early-career flag two views are reported:

- **On the sample**: every labelled posting counts once.
- **Estimated for all eligible postings**: each sampled negative stands for 274 / 80 = 3.43 postings,
  the share of its stratum. Precision is the same in both views (all positives are labelled); recall is
  lower in the estimate because a missed posting among the negatives represents several.

The test fails when a score drops below a floor set just under the measured values, so a rule change
cannot make the classifier worse without anyone noticing.

## Results

First measurement, 2026-11-28, rules as of Milestone 1:

| Early career | On the sample | Estimated for all eligible postings |
|---|---|---|
| Precision | 100.0% | 100.0% |
| Recall | 95.8% | 87.0% |
| F1 | 97.9% | 93.1% |

| Opportunity type and domain | Accuracy |
|---|---|
| Opportunity type, all postings | 98.4% |
| Opportunity type, postings both call early career | 100.0% |
| Domain, all postings | 87.3% |
| Domain, early-career postings | 85.4% |

What the errors show:

- **Missed early-career postings (2).** Bitdefender's "Mobile Application Developer — Partner Apps" asks
  for "1+ years of experience", and "Technical Support Engineer with French" says previous experience
  "is a plus". Both signals sit deep in the description, beyond the excerpt.
- **No false positives.** Most rules positives are Bosch "Working Student" titles, which are
  unambiguous; entry-level postings are recognized from the SmartRecruiters experience-level field.
- **Domain errors** come from department names that point elsewhere ("Customer Service" for a facility
  role and for a Microsoft 365 engineer), from manufacturing "digitalization" roles, and from a
  description that mentions a "Research and Development Center" for a construction engineer.

### Changes made after the first measurement

- A description that asks for **at most one year** of experience, with no larger number of years
  anywhere (ranges count with their upper bound), now marks a junior role.
- Domain keywords: "information technology", "production IT", "platform engineer", "S/4HANA" (software);
  "reporting specialist", "digitalization" (data); "construction" (engineering); "training specialist",
  "trainer" (HR); "project manager", "program manager", "PMO", "facility", "transport", "cleaning"
  (operations). Each one comes from an error above and has a unit test.

Second measurement, same day, after these changes:

| Metric | Before | After |
|---|---|---|
| Early career: precision | 100.0% | 100.0% |
| Early career: recall on the sample / estimated | 95.8% / 87.0% | 95.8% / 87.0% |
| Opportunity type, all postings | 98.4% | 98.4% |
| Domain, all postings | 87.3% | **96.8%** |
| Domain, early-career postings | 85.4% | **95.8%** |

The domain errors that remain are two manufacturing "digitalization" roles, one labelled software and
one data, where the title alone cannot tell. Early career does not change on this set: the new
one-year rule needs the full description, which the test does not have (see Limitations). The floors
in the test were raised to just under the new values; they only go up.

## Limitations

- **Excerpt only.** The classifier sees the first 300 characters of the description here, not the whole
  text, so the scores are a lower bound. Both missed postings would need the full description.
- **Who labelled.** One person's reading, with the disagreements between labels and rules reviewed a
  second time. A future LLM classifier is compared on the same labels, which favours whatever agrees
  with that reading.
- **Small and skewed.** 126 postings, many from Bosch working-student roles; precision on other kinds of
  postings is less certain. The sample can be exported again and relabelled as the catalog grows.
