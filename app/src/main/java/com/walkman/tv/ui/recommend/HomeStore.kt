package com.walkman.tv.ui.recommend

import com.walkman.tv.data.model.BoardInfo
import com.walkman.tv.data.model.SonglistInfo
import com.walkman.tv.data.model.SonglistTag
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.source.catalog.Boards
import com.walkman.tv.source.catalog.Songlists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A hero banner card — top of the discover page. */
data class HeroItem(val songlist: SonglistInfo, val source: SourceID)

data class HomeState(
    val isLoading: Boolean = false,
    val heroes: List<HeroItem> = emptyList(),
    val recommendations: List<SonglistInfo> = emptyList(),
    val boards: List<BoardInfo> = emptyList(),
    val sources: List<SourceID> = emptyList(),
)

/**
 * Backs the discover (home) page right column. Implements the data flow in spec
 * docs/discover-page-spec-android-tv.md §3:
 *   1. Parallel fetch each enabled source's top recommended songlists (1st order, ALL tag, page 1).
 *   2. Hero pool = first songlist of each source.
 *   3. Recommendations row = drop-first interleave across sources, take 12.
 *   4. Boards row = quota-balanced across sources, target total 12.
 *
 * Re-runs only when the set of enabled sources actually changed (sorted-joined keys), so flipping
 * an unrelated setting doesn't refetch.
 */
class HomeStore(
    private val songlists: Songlists,
    private val boards: Boards,
) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    /** Identity of the last load — sorted joined source keys. Re-loading the same set is a no-op. */
    private var lastSourcesKey: String = ""

    fun loadIfNeeded(homeSources: Set<SourceID>, force: Boolean = false) {
        val srcs = SourceID.homePageOrder.filter { it in homeSources }
        val key = srcs.joinToString(",") { it.key }
        if (!force && key == lastSourcesKey && !_state.value.isLoading) return
        lastSourcesKey = key
        load(srcs)
    }

    private fun load(srcs: List<SourceID>) {
        loadJob?.cancel()
        if (srcs.isEmpty()) {
            _state.update {
                it.copy(
                    isLoading = false, heroes = emptyList(), recommendations = emptyList(),
                    boards = emptyList(), sources = emptyList(),
                )
            }
            return
        }
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true, sources = srcs) }

            // Section 3 step 1: parallel songlist fetch per source.
            val groups: Map<SourceID, List<SonglistInfo>> = coroutineScope {
                srcs.map { s ->
                    async {
                        val svc = songlists.serviceFor(s)
                        if (svc == null) return@async s to emptyList()
                        val order = svc.orders.firstOrNull() ?: return@async s to emptyList()
                        val list = runCatching { svc.fetchRecommended(order, SonglistTag.ALL, 1) }
                            .getOrDefault(emptyList())
                        s to list
                    }
                }.awaitAll().toMap()
            }

            // Step 2: hero pool — 1 per enabled source (the first of its recommendations).
            val heroes = srcs.mapNotNull { s -> groups[s]?.firstOrNull()?.let { HeroItem(it, s) } }

            // Step 3: recommendation tail interleave — never two in a row from the same source.
            val tails = srcs.associateWith { groups[it].orEmpty().drop(1).take(8) }
            val recs = mutableListOf<SonglistInfo>()
            val maxSize = tails.values.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxSize) {
                for (s in srcs) tails[s]?.getOrNull(i)?.let { recs += it }
            }
            val recommendations = recs.take(12)

            // Step 4: boards — parallel fetch, then quota-balance across enabled sources.
            val boardsBySource: Map<SourceID, List<BoardInfo>> = coroutineScope {
                srcs.map { s ->
                    async {
                        val svc = boards.serviceFor(s)
                        if (svc == null) return@async s to emptyList()
                        s to runCatching { svc.fetchBoards() }.getOrDefault(emptyList())
                    }
                }.awaitAll().toMap()
            }
            val boardList = balanceBoards(srcs, boardsBySource, totalTarget = 12)

            _state.update {
                it.copy(
                    isLoading = false,
                    heroes = heroes,
                    recommendations = recommendations,
                    boards = boardList,
                    sources = srcs,
                )
            }
        }
    }

    /**
     * Quota algorithm (spec §3): each source gets `totalTarget / n` slots; remaining slots are
     * round-robin'd to whichever source still has more to offer. Bail after 2n cursor laps to
     * avoid infinite loops when every source is exhausted.
     */
    private fun balanceBoards(
        srcs: List<SourceID>,
        bySource: Map<SourceID, List<BoardInfo>>,
        totalTarget: Int,
    ): List<BoardInfo> {
        val n = srcs.size
        if (n == 0) return emptyList()
        val perSource = (totalTarget / n).coerceAtLeast(1)
        val picked: Map<SourceID, MutableList<BoardInfo>> = srcs.associateWith { s ->
            bySource[s].orEmpty().take(perSource).toMutableList()
        }
        var remaining = totalTarget - picked.values.sumOf { it.size }
        var cursor = 0
        while (remaining > 0 && cursor <= 2 * n) {
            val s = srcs[cursor % n]
            cursor++
            val current = picked[s]!!.size
            val available = bySource[s]?.size ?: 0
            if (current < available) {
                picked[s]!!.add(bySource[s]!![current])
                remaining--
            }
        }
        // Stable per-source order then flatten.
        return srcs.flatMap { picked[it].orEmpty() }
    }
}
