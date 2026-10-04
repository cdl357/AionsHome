-- AionsHome 移动端 · Supabase RLS 授权（anon 直连用）
-- 用法：Supabase 控制台 → SQL Editor → 粘贴全部 → Run。可重复执行。
-- 说明：App 用 anon key 直连 REST，表开了 RLS 但没给 anon 放行的话，读到的永远是空。
-- 第一阶段（只读同步）只放行：朋友圈读/发、哥哥日记读。
-- 注意：故意【不】给 anon DELETE / UPDATE——App 端也没有实现远端删除和修改。

-- ── 朋友圈：anon 可读、可发（Yuri 自己发动态）；不开放删除/修改 ──
alter table public.moments enable row level security;

drop policy if exists "anon read moments" on public.moments;
create policy "anon read moments" on public.moments
    for select to anon using (true);

drop policy if exists "anon insert moments" on public.moments;
create policy "anon insert moments" on public.moments
    for insert to anon with check (true);

-- ── 朋友圈评论：anon 可读、可发（Yuri 评论同步到云） ──
alter table public.moment_comments enable row level security;

drop policy if exists "anon read moment_comments" on public.moment_comments;
create policy "anon read moment_comments" on public.moment_comments
    for select to anon using (true);

drop policy if exists "anon insert moment_comments" on public.moment_comments;
create policy "anon insert moment_comments" on public.moment_comments
    for insert to anon with check (true);

-- ── 日记：anon 只读（哥哥的日记 user_id = ai_哥哥）；不开放写入/删除 ──
alter table public.diary_entries enable row level security;

drop policy if exists "anon read diaries" on public.diary_entries;
create policy "anon read diaries" on public.diary_entries
    for select to anon using (true);

-- ── home_quote（情话）：当前数据库没有这张表，暂不需要执行；建表后再来加政策 ──
