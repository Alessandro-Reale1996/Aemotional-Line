import { useAccount } from '../app/context';

/** A stand-in home for F4: F5 brings the home of each role (frontend point 5). */
export function HomePage()
{
  const account = useAccount();

  return (
    <>
      <h1 className="h4 mb-3">Ciao, {account?.displayName}</h1>
      <p className="text-body-secondary">Le schermate arrivano con i prossimi passi.</p>
    </>
  );
}
