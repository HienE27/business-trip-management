"use client";

type ProfileCardProps = {
  id: string;
  name: string;
  role: string;
  specialty: string;
  avatarUrl?: string;
};

export function ProfileCard({ id, name, namePrefix, role, specialty, avatarUrl }: ProfileCardProps & { namePrefix?: string }) {
  return (
    <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.1),_0_1px_2px_-1px_rgba(0,0,0,0.1)] p-6 flex flex-col items-center text-center">
      <div className="w-32 h-32 rounded-full overflow-hidden mb-4 border-4 border-surface-container-low relative">
        {avatarUrl ? (
          <img alt={name} className="w-full h-full object-cover" src={avatarUrl} />
        ) : (
          <div className="w-full h-full bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-4xl">
            {name.split(" ").slice(-2).map((p) => p[0]).join("")}
          </div>
        )}
        <div className="absolute bottom-1 right-1 w-4 h-4 bg-secondary-fixed rounded-full border-2 border-surface-container-lowest" />
      </div>

      <h2 className="font-title-lg text-on-surface mb-1">{namePrefix} {name}</h2>
      <p className="font-body-md text-on-surface-variant mb-1">ID: {id}</p>
      <p className="font-label-md text-primary bg-primary-fixed px-3 py-1 rounded-full mb-6">{specialty}</p>

      <div className="w-full space-y-3">
        <button
          className="w-full bg-primary-container text-on-primary-container h-10 rounded-lg font-label-md text-label-md hover:bg-primary hover:text-on-primary transition-colors flex items-center justify-center gap-2"
          type="button"
        >
          <span className="material-symbols-outlined text-sm">edit</span>
          Chinh sua ho so
        </button>
        <button
          className="w-full bg-transparent text-primary border border-primary h-10 rounded-lg font-label-md text-label-md hover:bg-primary-fixed transition-colors flex items-center justify-center gap-2"
          type="button"
        >
          <span className="material-symbols-outlined text-sm">lock_reset</span>
          Dat lai mat khau
        </button>
      </div>
    </div>
  );
}
