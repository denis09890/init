import { Route, Routes } from 'react-router'
import Layout from './components/Layout.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import PostingPage from './pages/PostingPage.tsx'
import PostingsPage from './pages/PostingsPage.tsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<PostingsPage />} />
        <Route path="postings/:id" element={<PostingPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
