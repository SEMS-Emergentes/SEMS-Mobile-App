package pe.edu.upc.sems.reports

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pe.edu.upc.sems.reports.data.AndroidReportFiles
import pe.edu.upc.sems.reports.domain.ReportFiles

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportsModule {

    @Binds
    abstract fun bindReportFiles(impl: AndroidReportFiles): ReportFiles
}
