package com.example.financeplanner.ui.screens.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeplanner.ui.components.BalanceCard
import com.example.financeplanner.ui.components.ExpensePieChart
import com.example.financeplanner.ui.components.TransactionItem
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HomeScreen(
    onTransactionClick: (Long) -> Unit = {},
    onSeeAllClick: () -> Unit = {},
    isHistoryMode: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(parameters = { parametersOf(if (isHistoryMode) 0 else 5, isHistoryMode) })
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        text = if (isHistoryMode) "История операций" else "FinancePlanner",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                item {
                    BalanceCard(totalBalance = state.totalBalance)
                }
                if (state.expensesByCategory.isNotEmpty() && !isHistoryMode) {
                    item {
                        Text(
                            text = "Расходы по категориям",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    item {
                        ExpensePieChart(expensesByCategory = state.expensesByCategory)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item {
                    Text(
                        text = "Фильтр по категориям",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAllSelected = state.selectedCategory == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isAllSelected) Color.White.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .clickable { viewModel.onCategorySelected(null) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(text = "Все", color = Color.White, fontSize = 14.sp)
                        }

                        val categories = state.expensesByCategory.keys.toList()
                        categories.forEach { category ->
                            val isSelected = state.selectedCategory == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { viewModel.onCategorySelected(category) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Транзакции",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isHistoryMode) {
                            Text(
                                text = "Показать все",
                                color = Color(0xFF8B5CF6),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onSeeAllClick() }
                            )
                        }
                    }
                }
                if (state.transactions.isEmpty()) {
                    item {
                        Text(
                            text = "Нажмите «+», чтобы добавить транзакцию",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    items(state.transactions) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onClick = onTransactionClick
                        )
                    }
                }
            }
        }

        if (!isHistoryMode) {
            FloatingActionButton(
                onClick = { viewModel.addTestTransaction() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = Color.White.copy(alpha = 0.15f),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить транзакцию"
                )
            }
        }
    }
}