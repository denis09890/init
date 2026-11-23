import type { FormEvent, ReactNode } from 'react'
import { DOMAINS, OPPORTUNITY_TYPES, REMOTE_TYPES } from '../api/types.ts'
import type { Company } from '../api/types.ts'
import { activeFilterCount, LANGUAGES, PAID_OPTIONS, parseOption, toggle } from '../lib/filters.ts'
import type { FilterPatch, SearchFilters } from '../lib/filters.ts'
import { DOMAIN_LABELS, LANGUAGE_LABELS, OPPORTUNITY_TYPE_LABELS, REMOTE_LABELS } from '../lib/labels.ts'

interface FilterPanelProps {
  filters: SearchFilters
  companies: Company[]
  onChange: (patch: FilterPatch) => void
  onClear: () => void
}

export default function FilterPanel({ filters, companies, onChange, onClear }: FilterPanelProps) {
  function submitText(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    onChange({ q: String(form.get('q') ?? '').trim(), city: String(form.get('city') ?? '').trim() })
  }

  return (
    <div className="space-y-6">
      {/* The key makes the inputs start over when the URL changes (back button, "clear"). */}
      <form key={`${filters.q}|${filters.city}`} role="search" onSubmit={submitText} className="space-y-3">
        <Field label="Search" htmlFor="q">
          <input
            id="q"
            name="q"
            type="search"
            defaultValue={filters.q}
            placeholder="e.g. internship, practică"
            className="field"
          />
        </Field>
        <Field label="City" htmlFor="city">
          <input id="city" name="city" type="text" defaultValue={filters.city} placeholder="e.g. Cluj" className="field" />
        </Field>
        <button type="submit" className="btn-primary w-full">
          Search
        </button>
      </form>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Opportunity type</legend>
        <div className="flex flex-wrap gap-2">
          {OPPORTUNITY_TYPES.map((type) => (
            <button
              key={type}
              type="button"
              className="chip"
              aria-pressed={filters.types.includes(type)}
              onClick={() => onChange({ types: toggle(filters.types, type) })}
            >
              {OPPORTUNITY_TYPE_LABELS[type]}
            </button>
          ))}
        </div>
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Domain</legend>
        <div className="flex flex-wrap gap-2">
          {DOMAINS.map((domain) => (
            <button
              key={domain}
              type="button"
              className="chip"
              aria-pressed={filters.domains.includes(domain)}
              onClick={() => onChange({ domains: toggle(filters.domains, domain) })}
            >
              {DOMAIN_LABELS[domain]}
            </button>
          ))}
        </div>
      </fieldset>

      <Field label="Company" htmlFor="company">
        <select
          id="company"
          className="field"
          value={filters.company}
          onChange={(event) => onChange({ company: event.target.value })}
        >
          <option value="">All companies</option>
          {companies.map((company) => (
            <option key={company.slug} value={company.slug}>
              {company.name} ({company.earlyCareerPostings})
            </option>
          ))}
        </select>
      </Field>

      <Field label="Workplace" htmlFor="remote">
        <select
          id="remote"
          className="field"
          value={filters.remote}
          onChange={(event) => onChange({ remote: parseOption(event.target.value, REMOTE_TYPES) ?? '' })}
        >
          <option value="">Any</option>
          {REMOTE_TYPES.map((remote) => (
            <option key={remote} value={remote}>
              {REMOTE_LABELS[remote]}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Language" htmlFor="language">
        <select
          id="language"
          className="field"
          value={filters.language}
          onChange={(event) => onChange({ language: parseOption(event.target.value, LANGUAGES) ?? '' })}
        >
          <option value="">Any</option>
          {LANGUAGES.map((language) => (
            <option key={language} value={language}>
              {LANGUAGE_LABELS[language]}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Pay" htmlFor="paid">
        <select
          id="paid"
          className="field"
          value={filters.paid}
          onChange={(event) => onChange({ paid: parseOption(event.target.value, PAID_OPTIONS) ?? '' })}
        >
          <option value="">Any</option>
          <option value="true">Paid</option>
          <option value="false">Unpaid</option>
        </select>
      </Field>

      <div className="space-y-2">
        <Checkbox
          checked={filters.allLevels}
          onChange={(checked) => onChange({ allLevels: checked })}
          label="Also show roles that are not early career"
        />
        <Checkbox
          checked={filters.outsideRomania}
          onChange={(checked) => onChange({ outsideRomania: checked })}
          label="Also show roles outside Romania"
        />
      </div>

      <button type="button" className="btn-secondary w-full" onClick={onClear} disabled={activeFilterCount(filters) === 0}>
        Clear all filters
      </button>
    </div>
  )
}

interface FieldProps {
  label: string
  htmlFor: string
  children: ReactNode
}

function Field({ label, htmlFor, children }: FieldProps) {
  return (
    <div className="space-y-1">
      <label htmlFor={htmlFor} className="block text-sm font-medium">
        {label}
      </label>
      {children}
    </div>
  )
}

interface CheckboxProps {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
}

function Checkbox({ checked, onChange, label }: CheckboxProps) {
  return (
    <label className="flex items-start gap-2 text-sm">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-0.5 size-4 rounded accent-indigo-600"
      />
      <span>{label}</span>
    </label>
  )
}
