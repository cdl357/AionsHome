-- AionsHome 移动端 · Supabase RLS 授权（anon 直连用）
-- 用法：Supabase 控制台 → SQL Editor → 粘贴全部 → Run。跑一次即可，可重复执行。
-- 说明：App 用 anon key 直连 REST，表开了 RLS 但没给 anon 放行的话，读到的永远是空。

-- ── 朋友圈：anon 可读 / 可发 / 可删（自用小家，不做细粒度） ──
alter table public.moments enable row level security;

drop policy if exists "anon read moments" on public.moments;
create policy "anon read moments" on public.moments
    for select to anon using (true);

drop policy if exists "anon insert moments" on public.moments;
create policy "anon insert moments" on public.moments
    for insert to anon with check (true);

drop policy if exists "anon delete moments" on public.moments;
create policy "anon delete moments" on public.moments
    for delete to anon using (true);

-- ── 日记：anon 只读（Sean 的日记 user_id = ai_哥哥） ──
alter table public.diary_entries enable row level security;

drop policy if exists "anon read diaries" on public.diary_entries;
create policy "anon read diaries" on public.diary_entries
    for select to anon using (true);

-- ── 今日情话（可选；没这张表可整段跳过） ──
alter table public.home_quote enable row level security;

drop policy if exists "anon read quote" on public.home_quote;
create policy "anon read quote" on public.home_quote
    for select to anon using (true);

drop policy if exists "anon insert quote" on public.home_quote;
create policy "anon insert quote" on public.home_quote
    for insert to anon with check (true);

-- ── 朋友圈配图（可选）：Storage 公开桶，建一次即可 ──
-- insert into storage.buckets (id, name, public) values ('moments', 'moments', true)
--     on conflict (id) do nothing;
