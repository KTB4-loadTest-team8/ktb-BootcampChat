import { redirect } from 'next/navigation';

// The actual login form is still served by the pages-router entry at "/".
// Redirect on the server so /login never leaves a client-only blank page while
// the app-router hydration or navigation is delayed under load.
export default async function LoginRedirectPage({ searchParams }) {
  const params = await searchParams;
  const queryString = params ? new URLSearchParams(params).toString() : '';

  redirect(queryString ? `/?${queryString}` : '/');
}
