'use client'

import { useCallback, useEffect, useState } from 'react'
import { KeyRound, Loader2, MapPin, Pencil, Plus, Star, Trash2, UserRound } from 'lucide-react'
import * as accountApi from '@/lib/api/account'
import Modal from '@/components/Modal'
import { EmptyState, Loading } from '@/components/Feedback'
import { useAuth } from '@/contexts/AuthContext'
import { useToast } from '@/contexts/ToastContext'
import { saveUser } from '@/lib/auth'
import type { AddressRequest, AddressResponse } from '@/types'

const EMPTY_ADDRESS: AddressRequest = {
  label: '',
  receiverName: '',
  phone: '',
  street: '',
  ward: '',
  district: '',
  province: '',
  makeDefault: false,
}

export default function AccountView() {
  const { user, reloadUser, logout } = useAuth()
  const toast = useToast()

  const [profile, setProfile] = useState({ fullName: '', phone: '' })
  const [savingProfile, setSavingProfile] = useState(false)

  const [pw, setPw] = useState({ current: '', next: '', confirm: '' })
  const [savingPw, setSavingPw] = useState(false)

  useEffect(() => {
    if (user) setProfile({ fullName: user.fullName ?? '', phone: user.phone ?? '' })
  }, [user])

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    setSavingProfile(true)
    try {
      saveUser(await accountApi.updateProfile(profile.fullName.trim(), profile.phone))
      await reloadUser()
      toast.success('Đã lưu hồ sơ')
    } catch (err) {
      toast.showError(err, 'Không lưu được hồ sơ')
    } finally {
      setSavingProfile(false)
    }
  }

  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (pw.next !== pw.confirm) {
      toast.error('Mật khẩu nhập lại không khớp')
      return
    }
    setSavingPw(true)
    try {
      await accountApi.changePassword(pw.current, pw.next)
      toast.success('Đã đổi mật khẩu. Vui lòng đăng nhập lại.')
      // Backend đã thu hồi MỌI phiên, kể cả phiên này - đăng xuất ngay cho rõ ràng
      // thay vì để người dùng bị đá ra đột ngột ở lần gọi API sau 15 phút.
      setTimeout(() => void logout(), 1200)
    } catch (err) {
      toast.showError(err, 'Không đổi được mật khẩu')
      setSavingPw(false)
    }
  }

  if (!user) return <Loading />

  return (
    <div className="flex flex-col gap-5 max-w-3xl">
      <div>
        <h1 className="page-title">Tài khoản</h1>
        <p className="page-desc">{user.email}</p>
      </div>

      <div className="grid gap-5 md:grid-cols-2 items-start">
        <form onSubmit={saveProfile} className="card-padded flex flex-col gap-3">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
            <UserRound size={16} /> Hồ sơ
          </h2>
          <div>
            <label className="form-label" htmlFor="ac-name">Họ tên</label>
            <input id="ac-name" required maxLength={150} value={profile.fullName} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} className="form-input" />
          </div>
          <div>
            <label className="form-label" htmlFor="ac-phone">Số điện thoại</label>
            <input id="ac-phone" pattern="0\d{9}" title="10 chữ số, bắt đầu bằng 0" value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} className="form-input" />
            <p className="form-hint">Email là tên đăng nhập nên không đổi được ở đây.</p>
          </div>
          <button type="submit" disabled={savingProfile} className="btn btn-primary w-fit">
            {savingProfile && <Loader2 size={14} className="animate-spin" />} Lưu hồ sơ
          </button>
        </form>

        <form onSubmit={changePassword} className="card-padded flex flex-col gap-3">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
            <KeyRound size={16} /> Đổi mật khẩu
          </h2>
          <div>
            <label className="form-label" htmlFor="ac-cur">Mật khẩu hiện tại</label>
            <input id="ac-cur" type="password" required autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} className="form-input" />
          </div>
          <div>
            <label className="form-label" htmlFor="ac-new">Mật khẩu mới</label>
            <input id="ac-new" type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} className="form-input" />
          </div>
          <div>
            <label className="form-label" htmlFor="ac-confirm">Nhập lại mật khẩu mới</label>
            <input id="ac-confirm" type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} className="form-input" />
            <p className="form-hint">Đổi xong, mọi thiết bị đang đăng nhập sẽ bị đăng xuất.</p>
          </div>
          <button type="submit" disabled={savingPw} className="btn btn-primary w-fit">
            {savingPw && <Loader2 size={14} className="animate-spin" />} Đổi mật khẩu
          </button>
        </form>
      </div>

      {user.role === 'CUSTOMER' && <AddressBook />}
    </div>
  )
}

// ── Sổ địa chỉ ────────────────────────────────────────────────────────────

function AddressBook() {
  const toast = useToast()
  const [list, setList] = useState<AddressResponse[] | null>(null)
  const [editing, setEditing] = useState<{ id: number | null; form: AddressRequest } | null>(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    try {
      setList(await accountApi.listAddresses())
    } catch (err) {
      toast.showError(err, 'Không tải được sổ địa chỉ')
      setList([])
    }
    // toast không đưa vào deps: context dựng lại mỗi lần có thông báo mới
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editing) return
    setSaving(true)
    try {
      const req = { ...editing.form, label: editing.form.label?.trim() || undefined }
      if (editing.id == null) await accountApi.createAddress(req)
      else await accountApi.updateAddress(editing.id, req)
      toast.success('Đã lưu địa chỉ')
      setEditing(null)
      await load()
    } catch (err) {
      toast.showError(err, 'Không lưu được địa chỉ')
    } finally {
      setSaving(false)
    }
  }

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn()
      toast.success(ok)
      await load()
    } catch (err) {
      toast.showError(err)
    }
  }

  const f = editing?.form
  const set = (patch: Partial<AddressRequest>) => editing && setEditing({ ...editing, form: { ...editing.form, ...patch } })

  return (
    <section className="card-padded">
      <div className="flex items-center justify-between gap-3 mb-4">
        <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
          <MapPin size={16} /> Sổ địa chỉ
        </h2>
        <button
          onClick={() => setEditing({ id: null, form: { ...EMPTY_ADDRESS } })}
          disabled={list != null && list.length >= 10}
          className="btn btn-secondary btn-sm"
        >
          <Plus size={13} /> Thêm địa chỉ
        </button>
      </div>

      {list == null ? (
        <Loading />
      ) : list.length === 0 ? (
        <EmptyState icon={MapPin} title="Chưa có địa chỉ nào" desc="Lưu địa chỉ để lần thanh toán sau chỉ cần chọn." />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {list.map((a) => (
            <li key={a.id} className={`rounded-lg border p-3 flex flex-col gap-1 ${a.isDefault ? 'border-primary-300 dark:border-primary-700' : 'border-slate-200 dark:border-surface-border'}`}>
              <div className="flex items-center gap-2">
                <p className="font-bold text-sm text-slate-800 dark:text-slate-100">{a.label || 'Địa chỉ'}</p>
                {a.isDefault && <span className="badge badge-indigo">Mặc định</span>}
              </div>
              <p className="text-sm text-slate-700 dark:text-slate-200">{a.receiverName} · {a.phone}</p>
              <p className="text-2xs text-slate-500 dark:text-slate-400">
                {a.street}, {a.ward}, {a.district}, {a.province}
              </p>
              <div className="flex gap-1.5 mt-1.5">
                <button onClick={() => setEditing({ id: a.id, form: { label: a.label ?? '', receiverName: a.receiverName, phone: a.phone, street: a.street, ward: a.ward, district: a.district, province: a.province, makeDefault: false } })} className="btn btn-secondary btn-sm">
                  <Pencil size={12} /> Sửa
                </button>
                {!a.isDefault && (
                  <button onClick={() => void run(() => accountApi.setDefaultAddress(a.id), 'Đã đặt làm mặc định')} className="btn btn-secondary btn-sm">
                    <Star size={12} /> Mặc định
                  </button>
                )}
                <button
                  onClick={() => window.confirm('Xoá địa chỉ này?') && void run(() => accountApi.deleteAddress(a.id), 'Đã xoá địa chỉ')}
                  className="btn btn-danger btn-sm ml-auto"
                  aria-label="Xoá địa chỉ"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={editing != null}
        title={editing?.id == null ? 'Thêm địa chỉ' : 'Sửa địa chỉ'}
        onClose={() => setEditing(null)}
        wide
        footer={
          <>
            <button onClick={() => setEditing(null)} className="btn btn-secondary">Huỷ</button>
            <button type="submit" form="address-form" disabled={saving} className="btn btn-primary">
              {saving && <Loader2 size={14} className="animate-spin" />} Lưu
            </button>
          </>
        }
      >
        {f && (
          <form id="address-form" onSubmit={save} className="grid gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="ad-label">Tên gợi nhớ</label>
              <input id="ad-label" maxLength={50} value={f.label ?? ''} onChange={(e) => set({ label: e.target.value })} placeholder="Nhà riêng, Công ty…" className="form-input" />
            </div>
            <div>
              <label className="form-label" htmlFor="ad-name">Người nhận</label>
              <input id="ad-name" required maxLength={150} value={f.receiverName} onChange={(e) => set({ receiverName: e.target.value })} className="form-input" />
            </div>
            <div>
              <label className="form-label" htmlFor="ad-phone">Số điện thoại</label>
              <input id="ad-phone" required pattern="0\d{9}" title="10 chữ số, bắt đầu bằng 0" value={f.phone} onChange={(e) => set({ phone: e.target.value })} className="form-input" />
            </div>
            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="ad-street">Số nhà, tên đường</label>
              <input id="ad-street" required maxLength={255} value={f.street} onChange={(e) => set({ street: e.target.value })} className="form-input" />
            </div>
            <div>
              <label className="form-label" htmlFor="ad-ward">Phường / Xã</label>
              <input id="ad-ward" required maxLength={100} value={f.ward} onChange={(e) => set({ ward: e.target.value })} className="form-input" />
            </div>
            <div>
              <label className="form-label" htmlFor="ad-district">Quận / Huyện</label>
              <input id="ad-district" required maxLength={100} value={f.district} onChange={(e) => set({ district: e.target.value })} className="form-input" />
            </div>
            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="ad-province">Tỉnh / Thành phố</label>
              <input id="ad-province" required maxLength={100} value={f.province} onChange={(e) => set({ province: e.target.value })} className="form-input" />
            </div>
            <label className="sm:col-span-2 flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
              <input type="checkbox" checked={f.makeDefault} onChange={(e) => set({ makeDefault: e.target.checked })} />
              Đặt làm địa chỉ mặc định
            </label>
          </form>
        )}
      </Modal>
    </section>
  )
}
