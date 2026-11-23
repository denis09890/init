import type { Posting } from '../api/types.ts'
import { DOMAIN_LABELS, OPPORTUNITY_TYPE_LABELS, REMOTE_LABELS } from '../lib/labels.ts'
import Badge from './Badge.tsx'

interface PostingBadgesProps {
  posting: Posting
}

/** The type, domain and workplace of a posting, and whether it is paid. */
export default function PostingBadges({ posting }: PostingBadgesProps) {
  return (
    <ul className="flex flex-wrap gap-2">
      <li>
        <Badge tone="indigo">{OPPORTUNITY_TYPE_LABELS[posting.opportunityType]}</Badge>
      </li>
      <li>
        <Badge>{DOMAIN_LABELS[posting.domain]}</Badge>
      </li>
      {posting.remoteType !== 'UNKNOWN' && (
        <li>
          <Badge>{REMOTE_LABELS[posting.remoteType]}</Badge>
        </li>
      )}
      {posting.paid === true && (
        <li>
          <Badge tone="emerald">Paid</Badge>
        </li>
      )}
      {posting.paid === false && (
        <li>
          <Badge tone="amber">Unpaid</Badge>
        </li>
      )}
    </ul>
  )
}
