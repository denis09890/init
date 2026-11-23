import { useEffect } from 'react'

export const DEFAULT_TITLE = 'Init: early-career opportunities in Romania'

/** Sets the browser tab title while the calling page is shown. */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title
  }, [title])
}
